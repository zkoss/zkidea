package org.zkoss.zkidea.completion;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ISA-level unit tests for
 * {@link ZulScopeVarCompletionContributor#BINDING_ROOT_PATTERN},
 * {@link ZulScopeVarCompletionContributor#SYSTEM_ATTRS},
 * and the prefix-extraction formula.
 * Covers Groups 1, 2, and 3 from scope-var-completion.isa.feature.
 *
 * <p>No mocking required — all tests are pure regex, set-membership, or string operations.</p>
 *
 * <p>Both fields must be package-visible (not {@code private}) to compile:
 * <pre>
 *   BINDING_ROOT_PATTERN  regex (unescaped):
 *     @(?:load|bind|save|init|command|global-command)\s*\(\s*[^.)]*$
 *   SYSTEM_ATTRS  (10 reserved &lt;apply&gt; attribute names)
 * </pre>
 * </p>
 */
class ZulScopeVarPatternTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // GROUP 1  ZulScopeVarCompletionContributor.BINDING_ROOT_PATTERN
    //   Matches when cursor is at "root position" inside a scope-var annotation:
    //   no dot and no closing paren in the text after '('.
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void pattern_matchesLoad_atRoot() {
        assertTrue(ZulScopeVarCompletionContributor.BINDING_ROOT_PATTERN
                .matcher("@load(").find());
    }

    @Test
    void pattern_matchesBind_atRoot() {
        assertTrue(ZulScopeVarCompletionContributor.BINDING_ROOT_PATTERN
                .matcher("@bind(").find());
    }

    @Test
    void pattern_matchesSave_atRoot() {
        assertTrue(ZulScopeVarCompletionContributor.BINDING_ROOT_PATTERN
                .matcher("@save(").find());
    }

    @Test
    void pattern_matchesInit_atRoot() {
        assertTrue(ZulScopeVarCompletionContributor.BINDING_ROOT_PATTERN
                .matcher("@init(").find());
    }

    @Test
    void pattern_matchesCommand_atRoot() {
        assertTrue(ZulScopeVarCompletionContributor.BINDING_ROOT_PATTERN
                .matcher("@command(").find());
    }

    @Test
    void pattern_matchesGlobalCommand_atRoot() {
        assertTrue(ZulScopeVarCompletionContributor.BINDING_ROOT_PATTERN
                .matcher("@global-command(").find());
    }

    @Test
    void pattern_matches_withPartialIdentifierTyped() {
        // textBeforeCursor = "@load(vm"  — no dot, no paren — [^.)]*$ matches "vm"
        assertTrue(ZulScopeVarCompletionContributor.BINDING_ROOT_PATTERN
                .matcher("@load(vm").find());
    }

    @Test
    void pattern_matches_withLeadingWhitespaceBeforeIdentifier() {
        // "@load( vm" — space is not '.' or ')' so [^.)]*$ still matches
        assertTrue(ZulScopeVarCompletionContributor.BINDING_ROOT_PATTERN
                .matcher("@load( vm").find());
    }

    @Test
    void pattern_doesNotMatch_dotPresent() {
        // "." is in the excluded set [^.)]; after "vm." the anchor $ cannot be satisfied
        assertFalse(ZulScopeVarCompletionContributor.BINDING_ROOT_PATTERN
                .matcher("@load(vm.").find());
    }

    @Test
    void pattern_doesNotMatch_dotInChain() {
        // "vm.items" — the dot in the middle prevents [^.)] from matching to $
        assertFalse(ZulScopeVarCompletionContributor.BINDING_ROOT_PATTERN
                .matcher("@load(vm.items").find());
    }

    @Test
    void pattern_doesNotMatch_closingParenPresent() {
        // ')' is excluded by [^.)] — annotation argument list is already closed
        assertFalse(ZulScopeVarCompletionContributor.BINDING_ROOT_PATTERN
                .matcher("@load(vm)").find());
    }

    @Test
    void pattern_doesNotMatch_converterAnnotation() {
        // @converter arguments are class references, not scope variables
        assertFalse(ZulScopeVarCompletionContributor.BINDING_ROOT_PATTERN
                .matcher("@converter(").find());
    }

    @Test
    void pattern_doesNotMatch_validatorAnnotation() {
        // @validator arguments are class references, not scope variables
        assertFalse(ZulScopeVarCompletionContributor.BINDING_ROOT_PATTERN
                .matcher("@validator(").find());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GROUP 2  ZulScopeVarCompletionContributor.SYSTEM_ATTRS
    //   Must contain exactly the 10 reserved <apply> attribute names.
    //   Any other attribute name is a candidate scope variable.
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void systemAttrs_containsAllTenReservedAttributeNames() {
        List<String> expected = Arrays.asList(
                "templateURI", "template", "if", "unless",
                "forEach", "forEachBegin", "forEachEnd",
                "forEachStep", "forEachStatus", "forEachIndex"
        );
        assertTrue(ZulScopeVarCompletionContributor.SYSTEM_ATTRS.containsAll(expected),
                "SYSTEM_ATTRS must contain all 10 reserved <apply> attribute names");
    }

    @Test
    void systemAttrs_doesNotContainUserDefinedAttributeNames() {
        assertFalse(ZulScopeVarCompletionContributor.SYSTEM_ATTRS.contains("ctx"));
        assertFalse(ZulScopeVarCompletionContributor.SYSTEM_ATTRS.contains("data"));
        assertFalse(ZulScopeVarCompletionContributor.SYSTEM_ATTRS.contains("model"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GROUP 3  Prefix extraction formula
    //   lastOpenParen = textBeforeCursor.lastIndexOf('(')
    //   prefix = textBeforeCursor.substring(lastOpenParen + 1).stripLeading()
    //
    //   These tests document the formula as pure string arithmetic.
    //   The same logic is embedded inside fillCompletionVariants.
    // ═══════════════════════════════════════════════════════════════════════════

    private static String extractPrefix(String textBeforeCursor) {
        int lastOpenParen = textBeforeCursor.lastIndexOf('(');
        return lastOpenParen >= 0
                ? textBeforeCursor.substring(lastOpenParen + 1).stripLeading()
                : "";
    }

    @Test
    void prefixExtraction_emptyPrefix_cursorRightAfterOpenParen() {
        // "@load("  → lastOpenParen=5, substring(6)="", stripLeading=""
        assertEquals("", extractPrefix("@load("));
    }

    @Test
    void prefixExtraction_partialIdentifier_noLeadingWhitespace() {
        // "@load(vm"  → lastOpenParen=5, substring(6)="vm", stripLeading="vm"
        assertEquals("vm", extractPrefix("@load(vm"));
    }

    @Test
    void prefixExtraction_partialIdentifier_withLeadingWhitespace() {
        // "@load(  vm"  → lastOpenParen=5, substring(6)="  vm", stripLeading="vm"
        assertEquals("vm", extractPrefix("@load(  vm"));
    }

    @Test
    void prefixExtraction_emptyPrefix_onlyWhitespaceAfterOpenParen() {
        // "@load(   "  → lastOpenParen=5, substring(6)="   ", stripLeading=""
        assertEquals("", extractPrefix("@load(   "));
    }
}
