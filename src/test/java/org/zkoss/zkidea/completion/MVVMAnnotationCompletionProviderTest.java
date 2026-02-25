package org.zkoss.zkidea.completion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MVVMAnnotationCompletionProvider#isInsideAnnotationBody(String)}.
 *
 * <p>Bug: When the cursor is inside {@code @command(|)}, the completion list incorrectly
 * includes {@code @global-command()} because the {@code addElement} suppression only
 * checks whether any query token <em>starts with</em> the annotation name — it does not
 * detect that the cursor is already inside an annotation's parentheses.
 *
 * <p>The fix guards the annotation-suggestion block with
 * {@code isInsideAnnotationBody(annotVal)}: if there are more open parens than close
 * parens in the text before the cursor, we are inside an annotation body and must not
 * offer top-level annotation names as completions.
 */
class MVVMAnnotationCompletionProviderTest {

    // ── isInsideAnnotationBody — cursor IS inside annotation parens ───────────

    @Test
    void isInsideAnnotationBody_commandOpenParen_returnsTrue() {
        // annotVal = "@command(" → cursor is right inside the opening paren of @command
        assertTrue(MVVMAnnotationCompletionProvider.isInsideAnnotationBody("@command("));
    }

    @Test
    void isInsideAnnotationBody_loadWithPartialExpression_returnsTrue() {
        // annotVal = "@load(vm." → cursor is inside @load's parentheses, after "vm."
        assertTrue(MVVMAnnotationCompletionProvider.isInsideAnnotationBody("@load(vm."));
    }

    @Test
    void isInsideAnnotationBody_globalCommandOpenParen_returnsTrue() {
        // annotVal = "@global-command(" → cursor inside @global-command(...)
        assertTrue(MVVMAnnotationCompletionProvider.isInsideAnnotationBody("@global-command("));
    }

    // ── isInsideAnnotationBody — cursor is NOT inside annotation parens ───────

    @Test
    void isInsideAnnotationBody_emptyString_returnsFalse() {
        assertFalse(MVVMAnnotationCompletionProvider.isInsideAnnotationBody(""));
    }

    @Test
    void isInsideAnnotationBody_atSign_returnsFalse() {
        // User just typed "@" — no annotation yet, no open paren
        assertFalse(MVVMAnnotationCompletionProvider.isInsideAnnotationBody("@"));
    }

    @Test
    void isInsideAnnotationBody_partialAnnotationName_returnsFalse() {
        // "@comm" — still typing the annotation name, not inside parens yet
        assertFalse(MVVMAnnotationCompletionProvider.isInsideAnnotationBody("@comm"));
    }

    @Test
    void isInsideAnnotationBody_closedAnnotationFollowedBySpace_returnsFalse() {
        // "@load(vm.name) @com" — the first annotation is closed; depth returns to 0
        // so typing after the space is NOT inside any annotation body
        assertFalse(MVVMAnnotationCompletionProvider.isInsideAnnotationBody("@load(vm.name) @com"));
    }

    @Test
    void isInsideAnnotationBody_fullyClosedAnnotation_returnsFalse() {
        // "@command('save')" — closed paren, depth = 0
        assertFalse(MVVMAnnotationCompletionProvider.isInsideAnnotationBody("@command('save')"));
    }
}
