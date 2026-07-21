package org.zkoss.zkidea.preview;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-2 (doc/zul_preview_product_positioning.md §2): the feature must be named and
 * framed as <b>"Layout Preview"</b> in user-facing copy — never "live (app) preview".
 * The no-ZK-jars message is the one user-facing product-name string on a pure-logic
 * (no IntelliJ platform) seam, so it is locked here; the editor/pane names are locked
 * in {@link ZulPreviewFileEditorProviderTest}.
 */
class PreviewResultNamingTest {

    @Test
    void noZkJarsMessage_namesLayoutPreview_notLivePreview() {
        String message = PreviewResult.noZkJars().getMessage();
        String lower = message.toLowerCase(Locale.ROOT);

        assertTrue(message.contains("Layout Preview"),
                () -> "no-ZK message must name the feature \"Layout Preview\": " + message);
        assertFalse(lower.contains("live preview"),
                () -> "no-ZK message must not sell a \"live preview\" (M-2): " + message);
        assertFalse(lower.contains("live app"),
                () -> "no-ZK message must not sell a \"live app preview\" (M-2): " + message);
    }
}
