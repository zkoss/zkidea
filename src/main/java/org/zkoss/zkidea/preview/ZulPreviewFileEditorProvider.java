package org.zkoss.zkidea.preview;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Registers the split text+preview editor for {@code .zul} files (see
 * doc/feature_overview.md and tasks/zul-preview/PLAN.md §2 for the surrounding
 * architecture).
 *
 * <p>{@code accept()} is a cheap, PSI-free extension check (Android
 * {@code DesignerEditorProvider} precedent, RESEARCH.md U4-F6) so it never fires for
 * {@code zk.xml}/{@code lang-addon.xml}/plain {@code .xml} files, which share the same
 * built-in XML {@link com.intellij.openapi.fileTypes.FileType} as {@code .zul}
 * (see {@link org.zkoss.zkidea.dom.ZulDomUtil}) but do not end in {@code .zul}.
 *
 * <p>Not gated on {@code JBCefApp.isSupported()} here: whether the embedded browser is
 * available only decides what the preview half of the split shows (a live render vs. an
 * explanatory panel), not whether the split editor itself is offered.
 */
public class ZulPreviewFileEditorProvider implements FileEditorProvider, DumbAware {

    public static final String EDITOR_TYPE_ID = "zkidea-zul-preview";

    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return "zul".equalsIgnoreCase(file.getExtension());
    }

    @Override
    public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        TextEditor textEditor = (TextEditor) new PsiAwareTextEditorProvider().createEditor(project, file);
        ZulPreviewFileEditor previewEditor = new ZulPreviewFileEditor(project, file);
        return new TextEditorWithPreview(textEditor, previewEditor, "Layout Preview");
    }

    @Override
    public @NotNull FileEditorPolicy getPolicy() {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
    }

    @Override
    public @NotNull String getEditorTypeId() {
        return EDITOR_TYPE_ID;
    }
}
