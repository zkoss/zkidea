package org.zkoss.zkidea.preview;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-3 (doc/zul_preview_product_positioning.md §2): the in-pane hint must set the right
 * expectation for users who skip the docs — that bound values are placeholders and their
 * ViewModel does not run in the Layout Preview. This locks the reviewable copy; the actual
 * banner display (a JCEF-card overlay) is a manual runIde check (no JCEF in test mode).
 */
class LayoutPreviewHintTest {

    @Test
    void hintText_saysValuesArePlaceholdersAndViewModelDoesNotRun() {
        String text = LayoutPreviewHint.TEXT;
        String lower = text.toLowerCase(Locale.ROOT);

        assertTrue(lower.contains("placeholder"),
                () -> "hint must tell the user bound values are placeholders: " + text);
        assertTrue(lower.contains("viewmodel"),
                () -> "hint must name the ViewModel as the thing that isn't running: " + text);
        assertTrue(
                lower.contains("doesn't run") || lower.contains("does not run")
                        || lower.contains("never runs") || lower.contains("won't run")
                        || lower.contains("not run"),
                () -> "hint must state the ViewModel does not run here: " + text);
    }
}
