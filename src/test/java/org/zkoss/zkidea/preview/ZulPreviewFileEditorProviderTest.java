package org.zkoss.zkidea.preview;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.jcef.JBCefApp;

/**
 * Registration-level test for {@link ZulPreviewFileEditorProvider} -- the automated
 * slice of AC-5 (tasks/zul-preview/PLAN.md §5): opening a {@code .zul} file must offer
 * the ZUL preview provider, and opening {@code zk.xml}/{@code lang-addon.xml}/plain
 * {@code .xml} files (which share the same built-in XML FileType as {@code .zul}, see
 * {@code ZulDomUtil}) must NOT.
 *
 * <p>JCEF is unavailable in headless test mode (RESEARCH.md U4-F12), so this test only
 * exercises provider selection ({@code accept()}/{@code getPolicy()}), not JCEF-backed
 * preview content -- {@link ZulPreviewFileEditor} always falls back to a Swing message
 * panel when {@code JBCefApp.isSupported()} is false, so it is safe to construct
 * headlessly, but the actual rendered/refresh behaviour is covered by the manual script
 * under tasks/zul-preview/manual-qa/AC-5.md instead.
 */
public class ZulPreviewFileEditorProviderTest extends BasePlatformTestCase {

    public void testAccept_zulFile_true() {
        VirtualFile file = myFixture.configureByText("preview.zul", "<zk/>").getVirtualFile();

        FileEditorProvider provider = findZulPreviewProvider();

        assertNotNull("ZulPreviewFileEditorProvider must be registered", provider);
        assertTrue("must accept a .zul file", provider.accept(getProject(), file));
    }

    public void testAccept_zkXml_false() {
        VirtualFile file = myFixture.configureByText("zk.xml", "<zk/>").getVirtualFile();

        FileEditorProvider provider = findZulPreviewProvider();

        assertNotNull(provider);
        assertFalse("must NOT accept zk.xml", provider.accept(getProject(), file));
    }

    public void testAccept_langAddonXml_false() {
        VirtualFile file = myFixture.configureByText("lang-addon.xml", "<language-addon/>").getVirtualFile();

        FileEditorProvider provider = findZulPreviewProvider();

        assertNotNull(provider);
        assertFalse("must NOT accept lang-addon.xml", provider.accept(getProject(), file));
    }

    public void testAccept_plainXml_false() {
        VirtualFile file = myFixture.configureByText("plain.xml", "<root/>").getVirtualFile();

        FileEditorProvider provider = findZulPreviewProvider();

        assertNotNull(provider);
        assertFalse("must NOT accept a plain .xml file", provider.accept(getProject(), file));
    }

    public void testPolicy_hidesDefaultEditor() {
        FileEditorProvider provider = findZulPreviewProvider();

        assertNotNull(provider);
        assertEquals(FileEditorPolicy.HIDE_DEFAULT_EDITOR, provider.getPolicy());
    }

    /**
     * Exercises {@code createEditor()} itself, not just {@code accept()}. JCEF is
     * unavailable in this headless test JVM, so {@link ZulPreviewFileEditor} must take
     * its fallback-message-panel branch rather than requiring a real browser -- this is
     * the "must not require JCEF for registration-level tests" requirement from
     * tasks/zul-preview.md's E3 deliverable list.
     */
    public void testCreateEditor_headless_buildsSplitEditorWithFallbackPreview() {
        assertFalse("this test assumes headless test mode has no JCEF", JBCefApp.isSupported());
        VirtualFile file = myFixture.configureByText("preview.zul", "<zk/>").getVirtualFile();
        FileEditorProvider provider = findZulPreviewProvider();
        assertNotNull(provider);

        FileEditor editor = provider.createEditor(getProject(), file);
        try {
            assertTrue("createEditor() must return a TextEditorWithPreview",
                    editor instanceof TextEditorWithPreview);
            FileEditor preview = ((TextEditorWithPreview) editor).getPreviewEditor();
            assertNotNull("the preview half must be constructed even without JCEF", preview);
            assertNotNull("the fallback panel must still expose a component", preview.getComponent());
        } finally {
            Disposer.dispose(editor);
        }
    }

    /**
     * M-2 (doc/zul_preview_product_positioning.md §2): the split editor and its preview
     * pane must be named "Layout Preview" (never "ZUL Preview"/"live preview"), so a
     * first-paint-only layout render sets the right expectation before it shows.
     */
    public void testCreateEditor_namesFeatureLayoutPreview() {
        VirtualFile file = myFixture.configureByText("preview.zul", "<zk/>").getVirtualFile();
        FileEditorProvider provider = findZulPreviewProvider();
        assertNotNull(provider);

        FileEditor editor = provider.createEditor(getProject(), file);
        try {
            assertEquals("split editor must be named \"Layout Preview\" (M-2)",
                    "Layout Preview", editor.getName());
            FileEditor preview = ((TextEditorWithPreview) editor).getPreviewEditor();
            assertEquals("preview pane must be named \"Layout Preview\" (M-2)",
                    "Layout Preview", preview.getName());
        } finally {
            Disposer.dispose(editor);
        }
    }

    private FileEditorProvider findZulPreviewProvider() {
        for (FileEditorProvider provider : FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getExtensionList()) {
            if (provider instanceof ZulPreviewFileEditorProvider) {
                return provider;
            }
        }
        return null;
    }
}
