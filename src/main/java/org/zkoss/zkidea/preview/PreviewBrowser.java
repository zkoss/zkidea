package org.zkoss.zkidea.preview;

import com.intellij.openapi.Disposable;

import javax.swing.JComponent;

/**
 * The embedded browser the preview pane draws in, behind a seam that mentions no JCEF type.
 *
 * <p>This interface exists for one reason: {@link ZulPreviewFileEditor} must be able to
 * <em>link</em> in an IDE where JCEF is unreachable (issue #66 — since 2026.2 JCEF is a bundled
 * plugin that can be disabled, and before that it was absent from any non-JetBrains boot runtime).
 * A class that names {@code org.cef.*} or {@code com.intellij.ui.jcef.*} in its own bytecode fails
 * verification there, before any availability check it might contain can run. So every JCEF type
 * lives behind this interface, in {@link JcefPreviewBrowser}, which is loaded only after
 * {@link JcefAvailability#probe()} has confirmed JCEF is usable.
 *
 * <p>Implementations are disposed by the editor that created them
 * ({@code Disposer.register(editor, browser)}).
 */
interface PreviewBrowser extends Disposable {

    /** The component to show in the preview card. */
    JComponent getComponent();

    /** Re-fetches the current page (revalidating it rather than repainting from cache). */
    void reload();
}
