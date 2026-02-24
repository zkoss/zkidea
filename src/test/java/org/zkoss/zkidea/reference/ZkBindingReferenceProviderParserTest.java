package org.zkoss.zkidea.reference;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ISA-level unit tests for the pure-static parser methods of
 * {@link ZkBindingReferenceProvider}.
 *
 * These methods have no IntelliJ platform dependencies — no mocking required.
 * Covers Groups 4, 5, and 6 from mvvm_property_navigation.isa.feature.
 *
 * <ul>
 *   <li>Group 4 — {@code findMatchingParen(String, int)}</li>
 *   <li>Group 5 — {@code extractAnnotations(String)}</li>
 *   <li>Group 6 — {@code extractChains(String)}</li>
 * </ul>
 *
 * <p>TextRange offset convention (for reference when reading these tests):
 * <pre>
 *   valueOffset = 1  (one opening quote stripped)
 *   rangeStart  = valueOffset + bodyStartOffset + nameStartInBody
 *   rangeEnd    = rangeStart + nameLength
 * </pre>
 */
class ZkBindingReferenceProviderParserTest {

    // ── assertion helpers ────────────────────────────────────────────────────

    private void assertSingleAnnotation(String text,
                                        String expectedName,
                                        String expectedBody,
                                        int expectedBodyStartOffset) {
        List<ZkBindingReferenceProvider.AnnotationMatch> result =
                ZkBindingReferenceProvider.extractAnnotations(text);
        assertEquals(1, result.size(),
                "Expected exactly 1 AnnotationMatch for: " + text);
        ZkBindingReferenceProvider.AnnotationMatch m = result.get(0);
        assertEquals(expectedName,            m.name,            "name");
        assertEquals(expectedBody,            m.body,            "body");
        assertEquals(expectedBodyStartOffset, m.bodyStartOffset, "bodyStartOffset");
    }

    private void assertSegment(ZkBindingReferenceProvider.ChainSegment seg,
                               String name, int start, int len, boolean isMethodCall) {
        assertEquals(name,         seg.name,            "segment name");
        assertEquals(start,        seg.nameStartInBody, "nameStartInBody");
        assertEquals(len,          seg.nameLength,      "nameLength");
        assertEquals(isMethodCall, seg.isMethodCall,    "isMethodCall");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GROUP 4  ZkBindingReferenceProvider.findMatchingParen(String, int)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void findMatchingParen_loadVmList() {
        // @load(vm.list)  openPos=5 → ')' at 13
        assertEquals(13, ZkBindingReferenceProvider.findMatchingParen("@load(vm.list)", 5));
    }

    @Test
    void findMatchingParen_loadVmCrewName() {
        // @load(vm.crew.name)  openPos=5 → ')' at 18
        assertEquals(18, ZkBindingReferenceProvider.findMatchingParen("@load(vm.crew.name)", 5));
    }

    @Test
    void findMatchingParen_initVmList() {
        assertEquals(13, ZkBindingReferenceProvider.findMatchingParen("@init(vm.list)", 5));
    }

    @Test
    void findMatchingParen_bindVmList() {
        assertEquals(13, ZkBindingReferenceProvider.findMatchingParen("@bind(vm.list)", 5));
    }

    @Test
    void findMatchingParen_saveVmList() {
        assertEquals(13, ZkBindingReferenceProvider.findMatchingParen("@save(vm.list)", 5));
    }

    @Test
    void findMatchingParen_commandWithArgs() {
        // @command('delete', item=vm.selected)  openPos=8 → ')' at 35
        assertEquals(35,
                ZkBindingReferenceProvider.findMatchingParen(
                        "@command('delete', item=vm.selected)", 8));
    }

    @Test
    void findMatchingParen_quotedContentContainingCloseParen_notCountedAsDepth() {
        // @command('has(arg)')  — ')' inside single-quoted string must be skipped.
        // openPos=8 → ')' at 19
        assertEquals(19,
                ZkBindingReferenceProvider.findMatchingParen("@command('has(arg)')", 8));
    }

    @Test
    void findMatchingParen_noClosingParen_returnsNegOne() {
        assertEquals(-1, ZkBindingReferenceProvider.findMatchingParen("@load(vm.list", 5));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GROUP 5  ZkBindingReferenceProvider.extractAnnotations(String)
    // ═══════════════════════════════════════════════════════════════════════════

    // bodyStartOffset = length("@xxxx(") = 6 for all 4-char annotation names.

    @Test
    void extractAnnotations_loadVmList() {
        assertSingleAnnotation("@load(vm.list)", "load", "vm.list", 6);
    }

    @Test
    void extractAnnotations_loadVmName() {
        assertSingleAnnotation("@load(vm.name)", "load", "vm.name", 6);
    }

    @Test
    void extractAnnotations_loadVmActive() {
        assertSingleAnnotation("@load(vm.active)", "load", "vm.active", 6);
    }

    @Test
    void extractAnnotations_loadVmCrewName() {
        assertSingleAnnotation("@load(vm.crew.name)", "load", "vm.crew.name", 6);
    }

    @Test
    void extractAnnotations_initVmList() {
        assertSingleAnnotation("@init(vm.list)", "init", "vm.list", 6);
    }

    @Test
    void extractAnnotations_bindVmList() {
        assertSingleAnnotation("@bind(vm.list)", "bind", "vm.list", 6);
    }

    @Test
    void extractAnnotations_saveVmList() {
        assertSingleAnnotation("@save(vm.list)", "save", "vm.list", 6);
    }

    @Test
    void extractAnnotations_commandWithNestedArgs() {
        // DSL: "Navigate to property in @command expression parameter"
        // "@command(" = 9 chars → bodyStartOffset=9
        // body = "'delete', item=vm.selectedItem"
        assertSingleAnnotation(
                "@command('delete', item=vm.selectedItem)",
                "command",
                "'delete', item=vm.selectedItem",
                9);
    }

    @Test
    void extractAnnotations_plainText_returnsEmptyList() {
        // DSL: "No navigation outside of binding expression"
        assertTrue(ZkBindingReferenceProvider.extractAnnotations("plain text with vm.list").isEmpty());
    }

    @Test
    void extractAnnotations_unrecognizedAnnotationName_returnsEmptyList() {
        assertTrue(ZkBindingReferenceProvider.extractAnnotations("@unknown(vm.list)").isEmpty());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GROUP 6  ZkBindingReferenceProvider.extractChains(String)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void extractChains_vmList_twoSegments() {
        // body="vm.list" → [{vm,0,2,false},{list,3,4,false}]
        List<List<ZkBindingReferenceProvider.ChainSegment>> chains =
                ZkBindingReferenceProvider.extractChains("vm.list");

        assertEquals(1, chains.size());
        List<ZkBindingReferenceProvider.ChainSegment> chain = chains.get(0);
        assertEquals(2, chain.size());
        assertSegment(chain.get(0), "vm",   0, 2, false);
        assertSegment(chain.get(1), "list", 3, 4, false);
    }

    @Test
    void extractChains_vmName_twoSegments() {
        List<List<ZkBindingReferenceProvider.ChainSegment>> chains =
                ZkBindingReferenceProvider.extractChains("vm.name");
        assertEquals(1, chains.size());
        List<ZkBindingReferenceProvider.ChainSegment> chain = chains.get(0);
        assertEquals(2, chain.size());
        assertSegment(chain.get(0), "vm",   0, 2, false);
        assertSegment(chain.get(1), "name", 3, 4, false);
    }

    @Test
    void extractChains_vmActive_twoSegments() {
        // "active" has length 6
        List<List<ZkBindingReferenceProvider.ChainSegment>> chains =
                ZkBindingReferenceProvider.extractChains("vm.active");
        assertEquals(1, chains.size());
        List<ZkBindingReferenceProvider.ChainSegment> chain = chains.get(0);
        assertEquals(2, chain.size());
        assertSegment(chain.get(0), "vm",     0, 2, false);
        assertSegment(chain.get(1), "active", 3, 6, false);
    }

    @Test
    void extractChains_vmCrew_twoSegments() {
        List<List<ZkBindingReferenceProvider.ChainSegment>> chains =
                ZkBindingReferenceProvider.extractChains("vm.crew");
        assertEquals(1, chains.size());
        List<ZkBindingReferenceProvider.ChainSegment> chain = chains.get(0);
        assertEquals(2, chain.size());
        assertSegment(chain.get(0), "vm",   0, 2, false);
        assertSegment(chain.get(1), "crew", 3, 4, false);
    }

    @Test
    void extractChains_vmCrewName_threeSegments() {
        // DSL: "Navigate to getter on nested property path"
        // body="vm.crew.name"
        //   vm  : nameStartInBody=0, nameLength=2
        //   crew: nameStartInBody=3, nameLength=4
        //   name: nameStartInBody=8, nameLength=4  (3+4+1=8)
        List<List<ZkBindingReferenceProvider.ChainSegment>> chains =
                ZkBindingReferenceProvider.extractChains("vm.crew.name");

        assertEquals(1, chains.size());
        List<ZkBindingReferenceProvider.ChainSegment> chain = chains.get(0);
        assertEquals(3, chain.size());
        assertSegment(chain.get(0), "vm",   0, 2, false);
        assertSegment(chain.get(1), "crew", 3, 4, false);
        assertSegment(chain.get(2), "name", 8, 4, false);
    }

    @Test
    void extractChains_commandBody_skipStringLiteral_producesTwoChains() {
        // DSL: "Navigate to property in @command expression parameter"
        // body = "'delete', item=vm.selectedItem"
        //
        // Offset derivation:
        //   pos  0–7 : 'delete'  (string literal, skipped)
        //   pos  8   : ,
        //   pos  9   : (space)
        //   pos 10   : i — start of "item" (length 4)
        //   pos 14   : = — not '.' or '(' → chain [{item,10,4,false}] ends
        //   pos 15   : v — start of "vm" (length 2)
        //   pos 17   : .
        //   pos 18   : s — start of "selectedItem" (length 12)
        List<List<ZkBindingReferenceProvider.ChainSegment>> chains =
                ZkBindingReferenceProvider.extractChains("'delete', item=vm.selectedItem");

        assertEquals(2, chains.size());

        // Chain 0: just "item" (no vm prefix — will be filtered by processChain)
        List<ZkBindingReferenceProvider.ChainSegment> chain0 = chains.get(0);
        assertEquals(1, chain0.size());
        assertSegment(chain0.get(0), "item", 10, 4, false);

        // Chain 1: vm.selectedItem
        List<ZkBindingReferenceProvider.ChainSegment> chain1 = chains.get(1);
        assertEquals(2, chain1.size());
        assertSegment(chain1.get(0), "vm",           15, 2,  false);
        assertSegment(chain1.get(1), "selectedItem",  18, 12, false);
    }

    @Test
    void extractChains_emptyBody_returnsEmptyList() {
        assertTrue(ZkBindingReferenceProvider.extractChains("").isEmpty());
    }
}
