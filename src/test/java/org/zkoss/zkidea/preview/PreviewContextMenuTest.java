package org.zkoss.zkidea.preview;

import org.cef.browser.CefBrowser;
import org.cef.callback.CefMenuModel;
import org.cef.callback.CefStringVisitor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.atomic.AtomicReference;

import static org.cef.callback.CefMenuModel.MenuId.MENU_ID_USER_FIRST;
import static org.cef.callback.CefMenuModel.MenuId.MENU_ID_USER_LAST;
import static org.cef.callback.CefMenuModel.MenuId.MENU_ID_VIEW_SOURCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * CEF's built-in "View Source" item is dead in an embedded browser: the command is handled natively
 * inside CEF, which routes it into Chromium's open-in-a-new-tab path, and a {@code JBCefBrowser} in
 * an editor split has no tab to open. Nothing appears and no error is reported. We can't fix the
 * native handler from plugin code, so {@link PreviewContextMenu} drops the dead entry and offers a
 * working replacement built on {@code CefBrowser.getSource} instead.
 *
 * <p>The dump-display side is Swing/IDE-bound and has no headless seam (it is verified in
 * {@code ./gradlew runIde}); what is tested here is the menu surgery and the command routing, which
 * are pure.
 */
class PreviewContextMenuTest {

    @Test
    void theDeadBuiltInViewSourceItemIsRemoved() {
        CefMenuModel model = mock(CefMenuModel.class);

        PreviewContextMenu.customize(model);

        verify(model).remove(MENU_ID_VIEW_SOURCE);
    }

    @Test
    void aWorkingReplacementItemIsAdded() {
        CefMenuModel model = mock(CefMenuModel.class);

        PreviewContextMenu.customize(model);

        verify(model).addItem(PreviewContextMenu.VIEW_RENDERED_HTML_COMMAND_ID,
                PreviewContextMenu.VIEW_RENDERED_HTML_LABEL);
    }

    /**
     * Our handler shares the browser's {@code JBCefClient} with IntelliJ's own
     * {@code DefaultCefContextMenuHandler}, which claims {@code MENU_ID_USER_LAST} for its "Open
     * DevTools" item (JBCefBrowserBase). Reusing that id would shadow DevTools -- i.e. would break
     * the other half of this feature.
     */
    @Test
    void ourCommandIdCannotCollideWithIntelliJsDevToolsItem() {
        int id = PreviewContextMenu.VIEW_RENDERED_HTML_COMMAND_ID;
        assertTrue(id >= MENU_ID_USER_FIRST && id <= MENU_ID_USER_LAST,
                "must sit in CEF's user command-id range, got " + id);
        assertEquals(MENU_ID_USER_FIRST, id,
                "MENU_ID_USER_LAST is taken by IntelliJ's Open DevTools item");
    }

    @Test
    void invokingOurItemAsksTheBrowserForItsRenderedSource() {
        AtomicReference<String> shown = new AtomicReference<>();
        PreviewContextMenu handler = new PreviewContextMenu(shown::set);
        CefBrowser browser = mock(CefBrowser.class);

        boolean handled = handler.onContextMenuCommand(browser, null, null,
                PreviewContextMenu.VIEW_RENDERED_HTML_COMMAND_ID, 0);

        assertTrue(handled, "our own command must be reported as handled");
        ArgumentCaptor<CefStringVisitor> visitor = ArgumentCaptor.forClass(CefStringVisitor.class);
        verify(browser).getSource(visitor.capture());

        assertNull(shown.get(), "nothing is shown until CEF calls the visitor back");
        visitor.getValue().visit("<div class=\"z-window\">rendered</div>");
        assertEquals("<div class=\"z-window\">rendered</div>", shown.get());
    }

    /**
     * Every other command id must fall through untouched, or the handlers registered alongside ours
     * on the shared client -- IntelliJ's DevTools item, and CEF's own Back/Forward/Reload -- would
     * stop working.
     */
    @Test
    void everyOtherCommandFallsThroughToTheOtherHandlers() {
        PreviewContextMenu handler = new PreviewContextMenu(html -> {
            throw new AssertionError("must not run for a foreign command id");
        });
        CefBrowser browser = mock(CefBrowser.class);

        for (int foreign : new int[]{MENU_ID_USER_LAST, MENU_ID_VIEW_SOURCE, 102 /* MENU_ID_RELOAD */}) {
            assertFalse(handler.onContextMenuCommand(browser, null, null, foreign, 0),
                    "command " + foreign + " must be left to the other handlers");
        }
        verify(browser, never()).getSource(org.mockito.ArgumentMatchers.any());
    }

    /** A context menu with no source to dump must still not leave the dead entry behind. */
    @Test
    void menuSurgeryToleratesAModelThatHasNoViewSourceEntry() {
        CefMenuModel model = mock(CefMenuModel.class);
        // Mockito's default for remove(int) is false, i.e. "no such item" -- the CEF behaviour when
        // the menu is a selection/link menu rather than a page menu.
        PreviewContextMenu.customize(model);

        verify(model).remove(MENU_ID_VIEW_SOURCE);
        verify(model).addItem(anyInt(), anyString());
    }
}
