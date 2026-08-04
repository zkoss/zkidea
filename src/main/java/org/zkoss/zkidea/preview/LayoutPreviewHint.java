package org.zkoss.zkidea.preview;

import com.intellij.ide.util.PropertiesComponent;

/**
 * The in-pane hint shown above a Layout Preview render (M-3, see
 * doc/zul_preview_product_positioning.md §2). It sets the expectation — for the users who
 * never read the docs — that bound values are placeholders and their own ViewModel does
 * not execute here, so a first-paint layout with placeholder values is not mistaken for a
 * broken app.
 *
 * <p>Dismissal is persisted <b>application-wide</b> (not per project/file), so the banner
 * behaves as a genuine first-run notice: once the user clicks "Got it" it never returns.
 */
final class LayoutPreviewHint {

    /** Canonical hint copy — locked by {@code LayoutPreviewHintTest}. */
    static final String TEXT =
            "Binding values are shown as placeholders — your ViewModel doesn't run in the Layout Preview.";

    private static final String DISMISSED_KEY = "org.zkoss.zkidea.preview.layoutPreviewHintDismissed";

    private LayoutPreviewHint() {
    }

    static boolean isDismissed() {
        return PropertiesComponent.getInstance().getBoolean(DISMISSED_KEY, false);
    }

    static void dismiss() {
        PropertiesComponent.getInstance().setValue(DISMISSED_KEY, true);
    }
}
