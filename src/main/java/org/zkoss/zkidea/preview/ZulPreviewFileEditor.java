package org.zkoss.zkidea.preview;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.network.CefRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;

/**
 * The preview half of the ZUL split editor (see {@link ZulPreviewFileEditorProvider}).
 *
 * <p>Renders the file through the helper JVM managed by {@link ZulPreviewServerService}
 * inside a {@link JBCefBrowser}, or shows an explanatory Swing panel when JCEF is
 * unavailable (R5), no ZK jars were found on the module classpath (R7), or the preview
 * server failed to start.
 *
 * <p>Teardown: the browser, the refresh {@link MergingUpdateQueue}, and the VFS
 * message-bus connection are all registered with {@code this} as their parent
 * {@link com.intellij.openapi.Disposable}, so the platform's
 * {@code Disposer.dispose(this)} (invoked when the tab closes, see
 * {@code FileEditorProvider.disposeEditor}) tears them down automatically. The shared
 * preview server itself is intentionally left running -- see
 * {@link ZulPreviewServerService}'s class-level lifetime-policy comment.
 */
final class ZulPreviewFileEditor extends UserDataHolderBase implements FileEditor {

    private static final String CARD_LOADING = "loading";
    private static final String CARD_BROWSER = "browser";
    private static final String CARD_MESSAGE = "message";
    private static final String CARD_EXTERNAL = "external";

    private final Project project;
    private final VirtualFile file;
    private final JPanel component;
    private final CardLayout cardLayout;
    private final JTextArea messageArea;
    /** Non-null when the embedded browser (JCEF) is unavailable: the why + how-to-fix used by the
     * external-browser fallback card (P1). Null when JCEF is available. */
    private final JcefAvailability.Diagnosis jcefDiagnosis;
    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    private JBCefBrowser browser;
    private volatile String previewUrl;
    private volatile boolean disposed;
    /** The render target (build tool / docroot layout / resolved ZK jars) of the last preview
     * attempt, for the GitHub report links. Null until a target resolves -- the reporter then
     * falls back to the plain plugin/IDE/OS/JDK block. */
    private volatile String reportEnvironment;

    ZulPreviewFileEditor(@NotNull Project project, @NotNull VirtualFile file) {
        this.project = project;
        this.file = file;
        this.cardLayout = new CardLayout();
        this.component = new JPanel(cardLayout);

        this.messageArea = new JTextArea();
        messageArea.setEditable(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setBorder(JBUI.Borders.empty(12));
        JBScrollPane messageScroll = new JBScrollPane(messageArea);
        messageScroll.setBorder(BorderFactory.createEmptyBorder());
        // Every "can't display preview" message offers a one-click GitHub report
        // (prefilled with the message + environment; the user reviews and submits).
        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.add(messageScroll, BorderLayout.CENTER);
        JPanel reportBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        reportBar.setBorder(JBUI.Borders.empty(4, 12, 8, 12));
        reportBar.add(new ActionLink("Report this issue on GitHub", (ActionListener) e ->
                PreviewIssueReporter.report("[Layout Preview] Cannot display preview",
                        messageArea.getText(), currentSource(), reportEnvironment)));
        messagePanel.add(reportBar, BorderLayout.SOUTH);
        component.add(messagePanel, CARD_MESSAGE);
        component.add(new JLabel("Starting ZK preview server…", SwingConstants.CENTER), CARD_LOADING);

        // When JCEF is unavailable we don't stop here: the preview server still renders over
        // localhost, so we start it anyway and offer an external-browser fallback once it's READY
        // (see startPreview). The diagnosis explains *why* the in-pane browser is missing (P1).
        this.jcefDiagnosis = JBCefApp.isSupported() ? null : JcefAvailability.diagnose();

        cardLayout.show(component, CARD_LOADING);
        MergingUpdateQueue refreshQueue = new MergingUpdateQueue(
                "ZulPreviewRefresh", 300, true, null, this, null, Alarm.ThreadToUse.POOLED_THREAD);
        installRefreshListener(refreshQueue);
        startPreview();
    }

    private void startPreview() {
        ZulPreviewServerService.getInstance(project).preparePreview(file, result -> {
            if (disposed) {
                return;
            }
            reportEnvironment = result.getEnvironment();
            if (result.getStatus() == PreviewResult.Status.READY) {
                previewUrl = "http://localhost:" + result.getPort() + result.getRequestPath();
                if (jcefDiagnosis != null) {
                    showExternalBrowserFallback(jcefDiagnosis, previewUrl);
                } else {
                    browser = new JBCefBrowser(previewUrl);
                    Disposer.register(this, browser);
                    installExternalLinkHandler(browser);
                    component.add(wrapWithHint(browser.getComponent()), CARD_BROWSER);
                    cardLayout.show(component, CARD_BROWSER);
                }
            } else {
                showMessage(result.getMessage());
            }
        });
    }

    /**
     * JCEF is unavailable but the preview server started, so the render is reachable over
     * localhost: instead of a dead-end message, explain <em>why</em> the in-pane browser is
     * missing (the {@link JcefAvailability} diagnosis) and offer a one-click hand-off to the
     * system browser (P1). The GitHub report link mirrors the other can't-display cards.
     */
    private void showExternalBrowserFallback(JcefAvailability.Diagnosis diagnosis, String url) {
        JTextArea area = new JTextArea(diagnosis.getExplanation()
                + "\n\nThe preview is ready — you can open it in your system browser instead:");
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(JBUI.Borders.empty(12));
        JBScrollPane scroll = new JBScrollPane(area);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scroll, BorderLayout.CENTER);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        bar.setBorder(JBUI.Borders.empty(4, 12, 8, 12));
        bar.add(new ActionLink("Open preview in external browser", (ActionListener) e -> BrowserUtil.browse(url)));
        bar.add(new ActionLink("Report this issue on GitHub", (ActionListener) e ->
                PreviewIssueReporter.report("[Layout Preview] Cannot display preview",
                        area.getText(), currentSource(), reportEnvironment)));
        panel.add(bar, BorderLayout.SOUTH);

        component.add(panel, CARD_EXTERNAL);
        cardLayout.show(component, CARD_EXTERNAL);
    }

    /**
     * Routes external links (e.g. the error page's "Report on GitHub" link, or any
     * {@code <a href="http…">} in a rendered ZUL) to the system browser instead of letting
     * them navigate inside the preview pane, which would replace the render with a web page
     * and no way back. Localhost URLs (the preview itself and its {@code /zkau} resources)
     * are left to load in-pane. {@code onBeforeBrowse} fires only for navigations, not
     * sub-resource loads, so JS/CSS are unaffected.
     */
    private void installExternalLinkHandler(JBCefBrowser browser) {
        browser.getJBCefClient().addRequestHandler(new CefRequestHandlerAdapter() {
            @Override
            public boolean onBeforeBrowse(CefBrowser cefBrowser, CefFrame frame, CefRequest request,
                                          boolean userGesture, boolean isRedirect) {
                String url = request.getURL();
                if (userGesture && url != null && (url.startsWith("http://") || url.startsWith("https://"))
                        && !isLoopbackPreviewUrl(url)) {
                    BrowserUtil.browse(url);
                    return true; // cancel in-pane navigation
                }
                return false;
            }
        }, browser.getCefBrowser());
    }

    /**
     * Whether {@code url} points at the in-pane preview server (loopback) and so should load inside
     * the JCEF pane rather than being handed to the system browser. The host match requires an
     * authority boundary ({@code :}, {@code /}, or end-of-string) after the loopback host so a
     * look-alike such as {@code http://localhost.evil.example} is treated as external, not trusted
     * in-pane (review M3). {@code 127.0.0.1} is only ever used with an explicit port, so its {@code :}
     * is the boundary.
     */
    static boolean isLoopbackPreviewUrl(String url) {
        if (url == null) {
            return false;
        }
        return url.startsWith("http://127.0.0.1:")
                || isLoopbackHost(url, "http://localhost");
    }

    private static boolean isLoopbackHost(String url, String prefix) {
        if (!url.startsWith(prefix)) {
            return false;
        }
        if (url.length() == prefix.length()) {
            return true;
        }
        char next = url.charAt(prefix.length());
        return next == ':' || next == '/';
    }

    /**
     * Debounced (via {@code refreshQueue}) reload on save (AC-5): document-change-
     * without-save does not refresh in v1, only VFS content changes (i.e. after the
     * file is written to disk).
     *
     * <p>Refreshes via {@code reload()} rather than {@code loadURL(previewUrl)}. The URL never
     * changes for the life of this editor, so re-loading it is an ordinary same-URL navigation
     * that Chromium is free to answer out of its own cache -- and did: a live session was found
     * repainting the pre-edit render from {@code jcef_cache} while the server already had the new
     * one, and since the pane only refreshes on save, nothing would ever have corrected it. A
     * reload revalidates the page instead, while still letting the {@code /zkau/web/*} assets come
     * from cache. The launcher also marks the page {@code no-store} (see {@code PreviewHttpServer});
     * both ends are fixed because either one alone leaves the pane's correctness resting on
     * browser-cache heuristics.
     */
    private void installRefreshListener(MergingUpdateQueue refreshQueue) {
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                for (VFileEvent event : events) {
                    if (event instanceof VFileContentChangeEvent && file.equals(event.getFile())) {
                        refreshQueue.queue(Update.create("zul-preview-reload", () ->
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    if (browser != null && previewUrl != null) {
                                        browser.getCefBrowser().reload();
                                    }
                                })));
                        return;
                    }
                }
            }
        });
    }

    /**
     * Pins the M-3 first-run hint above the render, so a first-paint layout with
     * placeholder binding values is not read as a broken app. Skipped once the user has
     * dismissed it (persisted application-wide -- see {@link LayoutPreviewHint}).
     */
    private JComponent wrapWithHint(JComponent renderComponent) {
        if (LayoutPreviewHint.isDismissed()) {
            return renderComponent;
        }
        JPanel wrapper = new JPanel(new BorderLayout());
        EditorNotificationPanel banner = new EditorNotificationPanel(EditorNotificationPanel.Status.Info);
        banner.text(LayoutPreviewHint.TEXT);
        banner.createActionLabel("Got it", () -> {
            LayoutPreviewHint.dismiss();
            wrapper.remove(banner);
            wrapper.revalidate();
            wrapper.repaint();
        });
        wrapper.add(banner, BorderLayout.NORTH);
        wrapper.add(renderComponent, BorderLayout.CENTER);
        return wrapper;
    }

    /** The previewed file's current text (as loaded in the editor), for the issue report's
     * source block; {@code null} if no document is available. */
    private String currentSource() {
        try {
            Document document = FileDocumentManager.getInstance().getDocument(file);
            return document != null ? document.getText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void showMessage(String text) {
        messageArea.setText(text);
        cardLayout.show(component, CARD_MESSAGE);
    }

    @Override
    public @NotNull JComponent getComponent() {
        return component;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return browser != null ? browser.getComponent() : component;
    }

    @Override
    public @NotNull String getName() {
        return "Layout Preview";
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
        // No persisted state (v1): the preview always reflects the current file.
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public boolean isValid() {
        return file.isValid();
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    @Override
    public void dispose() {
        // See the class Javadoc: child resources are torn down via Disposer parenting.
        disposed = true;
    }
}
