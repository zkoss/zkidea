package org.zkoss.zkidea.reference;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ISA-level unit tests for {@link ZkTemplateUriReferenceProvider#TEMPLATE_URI_PATTERN}
 * and the startOffset calculation formula.
 * Covers Groups 2 and 3 from template-uri-navigation.isa.feature.
 *
 * <p>No mocking required — all tests are pure string/regex operations or arithmetic.</p>
 *
 * <p>{@code TEMPLATE_URI_PATTERN} must be package-visible (not {@code private}) to compile.
 * Regex: {@code @(?:load|init)\s*\(\s*['"]([^'"()]*)}</p>
 */
class ZkTemplateUriPatternTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // GROUP 2  ZkTemplateUriReferenceProvider.TEMPLATE_URI_PATTERN
    //   Capture group 1: starts at the leading '/' and extends to the first ' " ( ) char.
    //   matcher.start(1): position of '/' inside the value string (without outer XML quotes).
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void pattern_matchesLoad_singleQuotedAbsolutePath() {
        // "@load('" = 7 characters (indices 0-6) → '/' at index 7 → start(1)=7
        Matcher m = ZkTemplateUriReferenceProvider.TEMPLATE_URI_PATTERN
                .matcher("@load('/WEB-INF/template/grid.zul')");
        assertTrue(m.find());
        assertEquals("/WEB-INF/template/grid.zul", m.group(1));
        assertEquals(7, m.start(1));
    }

    @Test
    void pattern_matchesInit_singleQuotedAbsolutePath() {
        // "@init('" = 7 characters → '/' at index 7 → start(1)=7
        Matcher m = ZkTemplateUriReferenceProvider.TEMPLATE_URI_PATTERN
                .matcher("@init('/WEB-INF/template/item.zul')");
        assertTrue(m.find());
        assertEquals("/WEB-INF/template/item.zul", m.group(1));
        assertEquals(7, m.start(1));
    }

    @Test
    void pattern_matchesLoad_arbitraryAbsolutePath() {
        Matcher m = ZkTemplateUriReferenceProvider.TEMPLATE_URI_PATTERN
                .matcher("@load('/path/to/file.zul')");
        assertTrue(m.find());
        assertEquals("/path/to/file.zul", m.group(1));
        assertEquals(7, m.start(1));
    }

    @Test
    void pattern_matchesLoad_doubleQuotedAbsolutePath() {
        // Double-quote variant: "@load(\"" = 7 characters → '/' at index 7 → start(1)=7
        Matcher m = ZkTemplateUriReferenceProvider.TEMPLATE_URI_PATTERN
                .matcher("@load(\"/WEB-INF/template/grid.zul\")");
        assertTrue(m.find());
        assertEquals("/WEB-INF/template/grid.zul", m.group(1));
        assertEquals(7, m.start(1));
    }

    @Test
    void pattern_matchesIncompleteAnnotation_noClosingQuoteOrParen() {
        // DSL: pattern succeeds while the user is still typing (no closing ' or ')' present)
        Matcher m = ZkTemplateUriReferenceProvider.TEMPLATE_URI_PATTERN
                .matcher("@load('/WEB-INF/template/");
        assertTrue(m.find());
        assertEquals("/WEB-INF/template/", m.group(1));
        assertEquals(7, m.start(1));
    }

    @Test
    void pattern_matchesLoad_withOptionalWhitespaceBeforeQuote() {
        // \s* allows a space between '(' and the opening quote
        // "@load( '" = 8 characters → '/' at index 8 → start(1)=8
        Matcher m = ZkTemplateUriReferenceProvider.TEMPLATE_URI_PATTERN
                .matcher("@load( '/WEB-INF/grid.zul')");
        assertTrue(m.find());
        assertEquals("/WEB-INF/grid.zul", m.group(1));
        assertEquals(8, m.start(1));
    }

    @Test
    void pattern_doesNotMatch_relativePath_noLeadingSlash() {
        // Group 1 requires a literal '/' as its first character; 't' does not satisfy this
        Matcher m = ZkTemplateUriReferenceProvider.TEMPLATE_URI_PATTERN
                .matcher("@load('template/item.zul')");
        assertFalse(m.find());
    }

    @Test
    void pattern_doesNotMatch_bindAnnotation() {
        assertFalse(ZkTemplateUriReferenceProvider.TEMPLATE_URI_PATTERN
                .matcher("@bind('/WEB-INF/template/f.zul')").find());
    }

    @Test
    void pattern_doesNotMatch_saveAnnotation() {
        assertFalse(ZkTemplateUriReferenceProvider.TEMPLATE_URI_PATTERN
                .matcher("@save('/WEB-INF/template/f.zul')").find());
    }

    @Test
    void pattern_doesNotMatch_commandAnnotation() {
        assertFalse(ZkTemplateUriReferenceProvider.TEMPLATE_URI_PATTERN
                .matcher("@command('/WEB-INF/template/f.zul')").find());
    }

    @Test
    void pattern_doesNotMatch_unknownAnnotation() {
        assertFalse(ZkTemplateUriReferenceProvider.TEMPLATE_URI_PATTERN
                .matcher("@unknown('/WEB-INF/template/f.zul')").find());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GROUP 3  ZkTemplateUriReferenceProvider — startOffset calculation
    //   Formula: startOffset = valueOffset + matcher.start(1) + slashLen
    //   valueOffset = 1   (outer opening-quote of XmlAttributeValue is at PSI position 0)
    //   slashLen    = 1   (leading '/' is stripped; FileReferenceSet receives relative path)
    //   startOffset = 1 + matcher.start(1) + 1
    //
    // Position mapping for @load('/WEB-INF/template/grid.zul') (PSI element text):
    //   PSI pos: 0:'  1:@  2:l  3:o  4:a  5:d  6:(  7:'  8:/  9:W ← startOffset=9
    //            ↑                                                ↑
    //           outer quote                               first char of relative path
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void startOffset_loadStandardFormat_equals9() {
        // @load('/WEB-INF/template/grid.zul')
        //   "@load('" = 7 chars → matcher.start(1) = 7
        //   startOffset = 1 + 7 + 1 = 9
        Matcher m = ZkTemplateUriReferenceProvider.TEMPLATE_URI_PATTERN
                .matcher("@load('/WEB-INF/template/grid.zul')");
        assertTrue(m.find());
        assertEquals(9, 1 + m.start(1) + 1,
                "startOffset = valueOffset(1) + matcher.start(1)(7) + slashLen(1) = 9");
    }

    @Test
    void startOffset_initStandardFormat_equals9() {
        // @init('/WEB-INF/template/item.zul')
        //   "@init('" = 7 chars → matcher.start(1) = 7
        //   startOffset = 1 + 7 + 1 = 9
        Matcher m = ZkTemplateUriReferenceProvider.TEMPLATE_URI_PATTERN
                .matcher("@init('/WEB-INF/template/item.zul')");
        assertTrue(m.find());
        assertEquals(9, 1 + m.start(1) + 1);
    }

    @Test
    void startOffset_loadWithSpaceBeforeQuote_equals10() {
        // @load( '/WEB-INF/grid.zul')
        //   "@load( '" = 8 chars → matcher.start(1) = 8
        //   startOffset = 1 + 8 + 1 = 10
        //
        // PSI pos: 0:'  1:@  2:l  3:o  4:a  5:d  6:(  7:sp  8:'  9:/  10:W ← startOffset=10
        Matcher m = ZkTemplateUriReferenceProvider.TEMPLATE_URI_PATTERN
                .matcher("@load( '/WEB-INF/grid.zul')");
        assertTrue(m.find());
        assertEquals(10, 1 + m.start(1) + 1);
    }
}
