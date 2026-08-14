package org.zkoss.zkidea.preview;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.jcef.JBCefBrowser;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.network.CefRequest;

import javax.swing.JComponent;
import java.util.function.Consumer;

/**
 * The JCEF-backed {@link PreviewBrowser} -- and the <em>only</em> class in the preview package that
 * names a JCEF type. Everything else must keep linking in an IDE where JCEF is unreachable
 * (issue #66), which is why this class is reached exclusively through
 * {@link #create(String, Consumer)} after {@link JcefAvailability#probe()} reports JCEF usable:
 * an {@code invokestatic} whose declared return type is the JCEF-free {@link PreviewBrowser} loads
 * nothing at verification time, so the caller stays linkable without JCEF.
 */
final class JcefPreviewBrowser implements PreviewBrowser {

    private final JBCefBrowser browser;

    /**
     * Builds the browser pointed at {@code url}, reporting the live DOM of the render to
     * {@code onRenderedHtml} (on the EDT) when the user picks "View Rendered HTML".
     *
     * <p>{@code setEnableOpenDevToolsMenuItem}: adds IntelliJ's "Open DevTools" entry to the context
     * menu. For "the preview is blank" it is the only thing that shows the actual cause -- a JS
     * error, or a 404 on a {@code /zkau/web/*} resource -- which no amount of source-reading
     * reveals. It costs nothing until clicked; clicking it spawns a second (DevTools frontend)
     * browser in a dialog, parented to this browser's Disposer, so it is torn down with the tab.
     */
    static PreviewBrowser create(String url, Consumer<String> onRenderedHtml) {
        return new JcefPreviewBrowser(url, onRenderedHtml);
    }

    private JcefPreviewBrowser(String url, Consumer<String> onRenderedHtml) {
        this.browser = JBCefBrowser.createBuilder()
                .setUrl(url)
                .setEnableOpenDevToolsMenuItem(true)
                .build();
        installExternalLinkHandler();
        installSourceViewer(onRenderedHtml);
    }

    @Override
    public JComponent getComponent() {
        return browser.getComponent();
    }

    @Override
    public void reload() {
        browser.getCefBrowser().reload();
    }

    @Override
    public void dispose() {
        Disposer.dispose(browser);
    }

    /**
     * Routes external links (e.g. the error page's "Report on GitHub" link, or any
     * {@code <a href="http…">} in a rendered ZUL) to the system browser instead of letting
     * them navigate inside the preview pane, which would replace the render with a web page
     * and no way back. Localhost URLs (the preview itself and its {@code /zkau} resources)
     * are left to load in-pane. {@code onBeforeBrowse} fires only for navigations, not
     * sub-resource loads, so JS/CSS are unaffected.
     */
    private void installExternalLinkHandler() {
        browser.getJBCefClient().addRequestHandler(new CefRequestHandlerAdapter() {
            @Override
            public boolean onBeforeBrowse(CefBrowser cefBrowser, CefFrame frame, CefRequest request,
                                          boolean userGesture, boolean isRedirect) {
                String url = request.getURL();
                if (userGesture && url != null && (url.startsWith("http://") || url.startsWith("https://"))
                        && !ZulPreviewFileEditor.isLoopbackPreviewUrl(url)) {
                    BrowserUtil.browse(url);
                    return true; // cancel in-pane navigation
                }
                return false;
            }
        }, browser.getCefBrowser());
    }

    /**
     * Replaces CEF's dead built-in "View Source" item with one that works -- see
     * {@link PreviewContextMenu} for why the built-in one silently does nothing here.
     *
     * <p>{@code getSource} calls the visitor back on a CEF thread, so the hop to the EDT happens
     * here rather than inside the handler.
     */
    private void installSourceViewer(Consumer<String> onRenderedHtml) {
        browser.getJBCefClient().addContextMenuHandler(
                new PreviewContextMenu(html -> ApplicationManager.getApplication().invokeLater(
                        () -> onRenderedHtml.accept(html))),
                browser.getCefBrowser());
    }
}
