package org.zkoss.zkidea.preview;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;
import org.cef.handler.CefContextMenuHandlerAdapter;

import java.util.function.Consumer;

import static org.cef.callback.CefMenuModel.MenuId.MENU_ID_USER_FIRST;
import static org.cef.callback.CefMenuModel.MenuId.MENU_ID_VIEW_SOURCE;

/**
 * Gives the preview pane's context menu a working "show me the page source" entry, so a render that
 * looks empty can be debugged without leaving the IDE.
 *
 * <p>CEF ships a "View Source" item in its built-in menu, but in an embedded browser it does
 * nothing at all: the command is handled natively inside CEF, below the Java layer, and routes into
 * Chromium's open-the-source-in-a-new-tab path. A {@link com.intellij.ui.jcef.JBCefBrowser} living
 * in an editor split has no tab strip to open into and this plugin installs no
 * {@code CefLifeSpanHandler}, so the request is dropped silently. There is no Java-side hook to
 * repair that handler, so this one removes the dead entry and adds a replacement built on
 * {@code CefBrowser.getSource} instead. (Loading {@code view-source:} in-pane was the other option
 * and is worse: it would replace the render, and the pane has no Back button.)
 *
 * <p>The replacement is also the better tool for ZK. {@code getSource} hands back the <em>live DOM
 * markup</em> rather than the original response bytes, and for a ZK page those differ completely --
 * the response is a {@code zkmx([...])} bootstrap whose widget tree the client engine expands into
 * DOM (see {@code RenderEngine}). Only the DOM answers "is my component rendered but hidden?", which
 * is what a user reaches for this menu to find out.
 *
 * <p>The visitor callback arrives on a CEF thread, so the {@code onSource} consumer supplied by
 * {@link ZulPreviewFileEditor} is responsible for hopping to the EDT before it touches Swing or the
 * IDE. Keeping that out of here leaves the menu surgery and command routing headlessly testable.
 */
final class PreviewContextMenu extends CefContextMenuHandlerAdapter {

    /**
     * CEF's user-defined command range starts here. It must not be {@code MENU_ID_USER_LAST}: this
     * handler shares the browser's {@code JBCefClient} with IntelliJ's own
     * {@code DefaultCefContextMenuHandler}, which claims that id for its "Open DevTools" item.
     */
    static final int VIEW_RENDERED_HTML_COMMAND_ID = MENU_ID_USER_FIRST;

    static final String VIEW_RENDERED_HTML_LABEL = "View Rendered HTML";

    private final Consumer<String> onSource;

    PreviewContextMenu(Consumer<String> onSource) {
        this.onSource = onSource;
    }

    /**
     * Swaps CEF's dead entry for ours. {@code remove} is a no-op when the menu has no View Source
     * item -- CEF builds a different menu for selections and links than for the page itself.
     */
    static void customize(CefMenuModel model) {
        model.remove(MENU_ID_VIEW_SOURCE);
        model.addItem(VIEW_RENDERED_HTML_COMMAND_ID, VIEW_RENDERED_HTML_LABEL);
    }

    @Override
    public void onBeforeContextMenu(CefBrowser browser, CefFrame frame, CefContextMenuParams params,
                                    CefMenuModel model) {
        customize(model);
    }

    @Override
    public boolean onContextMenuCommand(CefBrowser browser, CefFrame frame, CefContextMenuParams params,
                                        int commandId, int eventFlags) {
        if (commandId != VIEW_RENDERED_HTML_COMMAND_ID) {
            return false; // leave every other id to CEF and to IntelliJ's DevTools handler
        }
        browser.getSource(onSource::accept);
        return true;
    }
}
