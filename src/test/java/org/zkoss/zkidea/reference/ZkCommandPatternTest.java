package org.zkoss.zkidea.reference;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ISA-level unit tests for {@link ZkBindingReferenceProvider#COMMAND_STRING_PATTERN}
 * and {@link ZkBindingReferenceProvider#BEFORE_AFTER_PATTERN}.
 * Covers Groups 5 and 6 from command-binding-navigation.isa.feature.
 *
 * <p>No mocking required — all tests are pure regex operations.</p>
 *
 * <p>Both patterns must be package-visible (not {@code private}) to compile:
 * <pre>
 *   COMMAND_STRING_PATTERN regex: @(?:command|global-command)\s*\(\s*['"]([^'"]+)['"]
 *   BEFORE_AFTER_PATTERN regex:   \b(?:before|after)\s*=\s*['"]([^'"]+)['"]
 * </pre>
 * </p>
 */
class ZkCommandPatternTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // GROUP 5  ZkBindingReferenceProvider.COMMAND_STRING_PATTERN
    //   Group 1: bare command name between the quote characters.
    //   Offset reminder (valueOffset=1):
    //     cmdStart = 1 + matcher.start(1)
    //     cmdEnd   = 1 + matcher.end(1)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void commandPattern_matches_commandSingleQuoted_saveItem() {
        // '@command(' = 9 chars (0-8), "'" at 9, 's' at 10
        // group(1) = "saveItem" (len=8): start=10, end=18
        // With valueOffset=1: TextRange(11, 19)
        Matcher m = ZkBindingReferenceProvider.COMMAND_STRING_PATTERN
                .matcher("@command('saveItem')");
        assertTrue(m.find());
        assertEquals("saveItem", m.group(1));
        assertEquals(10, m.start(1));
        assertEquals(18, m.end(1));
    }

    @Test
    void commandPattern_matches_commandSingleQuoted_delete() {
        // group(1) = "delete" (len=6): start=10, end=16 → TextRange(11, 17)
        Matcher m = ZkBindingReferenceProvider.COMMAND_STRING_PATTERN
                .matcher("@command('delete')");
        assertTrue(m.find());
        assertEquals("delete", m.group(1));
        assertEquals(10, m.start(1));
        assertEquals(16, m.end(1));
    }

    @Test
    void commandPattern_matches_commandSingleQuoted_save() {
        // group(1) = "save" (len=4): start=10, end=14 → TextRange(11, 15)
        Matcher m = ZkBindingReferenceProvider.COMMAND_STRING_PATTERN
                .matcher("@command('save')");
        assertTrue(m.find());
        assertEquals("save", m.group(1));
        assertEquals(10, m.start(1));
        assertEquals(14, m.end(1));
    }

    @Test
    void commandPattern_matches_globalCommand_refresh() {
        // '@global-command(' = 16 chars (0-15), "'" at 16, 'r' at 17
        // group(1) = "refresh" (len=7): start=17, end=24 → TextRange(18, 25)
        Matcher m = ZkBindingReferenceProvider.COMMAND_STRING_PATTERN
                .matcher("@global-command('refresh')");
        assertTrue(m.find());
        assertEquals("refresh", m.group(1));
        assertEquals(17, m.start(1));
        assertEquals(24, m.end(1));
    }

    @Test
    void commandPattern_matches_globalCommand_updateDashboard() {
        // "updateDashboard" len=15: start=17, end=32
        Matcher m = ZkBindingReferenceProvider.COMMAND_STRING_PATTERN
                .matcher("@global-command('updateDashboard')");
        assertTrue(m.find());
        assertEquals("updateDashboard", m.group(1));
        assertEquals(17, m.start(1));
    }

    @Test
    void commandPattern_matches_doubleQuotedCommandName() {
        // Double-quote variant: '@command("saveItem")' → start(1)=10
        Matcher m = ZkBindingReferenceProvider.COMMAND_STRING_PATTERN
                .matcher("@command(\"saveItem\")");
        assertTrue(m.find());
        assertEquals("saveItem", m.group(1));
        assertEquals(10, m.start(1));
    }

    @Test
    void commandPattern_matches_withOptionalSpaceBeforeQuote() {
        // \s* between '(' and quote: "@command( 'saveItem')" — start shifts by 1
        Matcher m = ZkBindingReferenceProvider.COMMAND_STRING_PATTERN
                .matcher("@command( 'saveItem')");
        assertTrue(m.find());
        assertEquals("saveItem", m.group(1));
    }

    @Test
    void commandPattern_doesNotMatch_loadAnnotation() {
        assertFalse(ZkBindingReferenceProvider.COMMAND_STRING_PATTERN
                .matcher("@load('someValue')").find());
    }

    @Test
    void commandPattern_doesNotMatch_saveAnnotation() {
        assertFalse(ZkBindingReferenceProvider.COMMAND_STRING_PATTERN
                .matcher("@save('someValue')").find());
    }

    @Test
    void commandPattern_doesNotMatch_bindAnnotation() {
        assertFalse(ZkBindingReferenceProvider.COMMAND_STRING_PATTERN
                .matcher("@bind('someValue')").find());
    }

    @Test
    void commandPattern_doesNotMatch_initAnnotation() {
        assertFalse(ZkBindingReferenceProvider.COMMAND_STRING_PATTERN
                .matcher("@init('someValue')").find());
    }

    @Test
    void commandPattern_doesNotMatch_emptyCommandName() {
        // [^'"]+ requires at least one character between the quotes
        assertFalse(ZkBindingReferenceProvider.COMMAND_STRING_PATTERN
                .matcher("@command('')").find());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GROUP 6  ZkBindingReferenceProvider.BEFORE_AFTER_PATTERN
    //   Applied to annotation body text, not the full attribute value.
    //   Offset reminder:
    //     cmdStart = bodyOffsetInElement + matcher.start(1)
    //     cmdEnd   = bodyOffsetInElement + matcher.end(1)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void beforeAfterPattern_matches_beforeSingleQuoted_validate_atStart() {
        // body = "before='validate'" — 'b' at 0
        // group(1) = "validate" (len=8): start=8, end=16
        Matcher m = ZkBindingReferenceProvider.BEFORE_AFTER_PATTERN
                .matcher("before='validate'");
        assertTrue(m.find());
        assertEquals("validate", m.group(1));
        assertEquals(8,  m.start(1));
        assertEquals(16, m.end(1));
    }

    @Test
    void beforeAfterPattern_matches_afterSingleQuoted_commit_atStart() {
        // body = "after='commit'" — 'a' at 0, "after=" = 6 chars, "'" at 6, 'c' at 7
        // group(1) = "commit" (len=6): start=7, end=13
        Matcher m = ZkBindingReferenceProvider.BEFORE_AFTER_PATTERN
                .matcher("after='commit'");
        assertTrue(m.find());
        assertEquals("commit", m.group(1));
        assertEquals(7,  m.start(1));
        assertEquals(13, m.end(1));
    }

    @Test
    void beforeAfterPattern_matches_beforeInAnnotationBody_validate() {
        // body = "vm.name, before='validate'"
        // 'b' at 9, "before='validate'" offset: group(1) start=17, end=25
        Matcher m = ZkBindingReferenceProvider.BEFORE_AFTER_PATTERN
                .matcher("vm.name, before='validate'");
        assertTrue(m.find());
        assertEquals("validate", m.group(1));
        assertEquals(17, m.start(1));
        assertEquals(25, m.end(1));
    }

    @Test
    void beforeAfterPattern_matches_afterInAnnotationBody_commit() {
        // body = "vm.name, after='commit'"
        // 'a' at 9, "after='commit'" offset: group(1) start=16, end=22
        Matcher m = ZkBindingReferenceProvider.BEFORE_AFTER_PATTERN
                .matcher("vm.name, after='commit'");
        assertTrue(m.find());
        assertEquals("commit", m.group(1));
        assertEquals(16, m.start(1));
        assertEquals(22, m.end(1));
    }

    @Test
    void beforeAfterPattern_matches_doubleQuotedGuardName() {
        // Double-quote variant
        Matcher m = ZkBindingReferenceProvider.BEFORE_AFTER_PATTERN
                .matcher("before=\"validateName\"");
        assertTrue(m.find());
        assertEquals("validateName", m.group(1));
    }

    @Test
    void beforeAfterPattern_matches_withSpacesAroundEquals() {
        // \s*=\s* allows spaces around the '='
        Matcher m = ZkBindingReferenceProvider.BEFORE_AFTER_PATTERN
                .matcher("before = 'validate'");
        assertTrue(m.find());
        assertEquals("validate", m.group(1));
    }

    @Test
    void beforeAfterPattern_doesNotMatch_unrelatedKeyword() {
        assertFalse(ZkBindingReferenceProvider.BEFORE_AFTER_PATTERN
                .matcher("converter='myConverter'").find());
    }

    @Test
    void beforeAfterPattern_doesNotMatch_midWordOccurrence() {
        // "notbefore" — 'before' is not at a word boundary → no match
        assertFalse(ZkBindingReferenceProvider.BEFORE_AFTER_PATTERN
                .matcher("notbefore='validate'").find());
    }
}
