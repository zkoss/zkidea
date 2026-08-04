package org.zkoss.zkpreview;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.zkoss.zkpreview.testutil.Variants;
import org.zkoss.zkpreview.testutil.ZkClasspathResolver;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P3 (tasks/zul-preview/PLAN-P3-syntax-corpus.md): a data-driven corpus that exercises every
 * ZUML syntax group (ZUML Reference) through the production launcher render path. Each
 * {@link SyntaxCase} is crossed with both servlet variants ({@link Variants#both()}), so every
 * case runs on javax (ZK 9.6) and jakarta (ZK 10) with one assertion switch.
 *
 * <p>Outcomes:
 * <ul>
 *   <li>{@code RENDERS} / {@code PLACEHOLDER} (success path): the render succeeds; the optional
 *       {@code present} marker must appear and the optional {@code absent} marker must not. The
 *       two differ only in intent for the matrix -- {@code RENDERS} asserts real output, while
 *       {@code PLACEHOLDER} asserts a value that is intentionally NOT the real data (e.g. an
 *       {@code if="false"} branch that is omitted, or an MVVM {@code @load} rendered as its
 *       placeholder expression text while the real bound value stays absent).</li>
 *   <li>{@code GRACEFUL_FAIL} (failure path): the render fails with a structured
 *       {@link RenderError} whose message contains {@code present} (and matches {@code phase}
 *       when set) -- never a raw stack trace.</li>
 * </ul>
 *
 * <p>Markers asserted via {@code html.contains(...)} are alphanumeric with no {@code '-'}: ZK 10
 * JS-escapes {@code '-'} as {@code '\-'} in rendered widget values, which would false-negative on
 * jakarta only (lesson #14). Fixture file names may contain hyphens.
 */
class ZulSyntaxCorpusTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");

    enum Outcome { RENDERS, PLACEHOLDER, GRACEFUL_FAIL }

    /**
     * One corpus case. {@code present}/{@code absent} are optional (null = not checked); together
     * they express binding-placeholder correctness (expression text present AND real value absent).
     * For {@code GRACEFUL_FAIL}, {@code present} is the substring the error message must contain.
     */
    record SyntaxCase(String group, String fixture, Outcome outcome,
                      String present, String absent, RenderPhase phase) {
        @Override
        public String toString() {
            return group + "/" + fixture;
        }
    }

    private static final List<SyntaxCase> CASES = List.of(
            // P3.0 smoke cases -- prove the cartesian harness and all three assertion branches.
            new SyntaxCase("smoke", "smoke-renders.zul", Outcome.RENDERS, "SMOKERENDERS", null, null),
            new SyntaxCase("smoke", "smoke-hidden.zul", Outcome.PLACEHOLDER, "always here", "SMOKEHIDDEN", null),
            new SyntaxCase("smoke", "smoke-missing-class.zul", Outcome.GRACEFUL_FAIL, "NoSuchClassXyz", null, null),

            // P3.1 -- elements & attributes
            new SyntaxCase("elements", "elem-basic.zul", Outcome.RENDERS, "ELEMBASIC", null, null),
            new SyntaxCase("elements", "elem-nested.zul", Outcome.RENDERS, "ELEMNESTED", null, null),
            new SyntaxCase("elements", "elem-attrs.zul", Outcome.RENDERS, "ELEMATTRS", null, null),

            // P3.1 -- <attribute> long form
            new SyntaxCase("attribute", "attr-text.zul", Outcome.RENDERS, "ATTRLONGFORM", null, null),
            // marker is the SECOND line -> proves the multiline body was captured past the newline.
            new SyntaxCase("attribute", "attr-multiline.zul", Outcome.RENDERS, "LINETWO", null, null),
            // load-bearing: the button's label is SET BY the long-form <attribute name="label">, so the
            // marker only appears if the long form applied (it also declares a long-form onClick handler).
            new SyntaxCase("attribute", "attr-event.zul", Outcome.RENDERS, "ATTREVENTLABEL", null, null),

            // P3.1 -- EL ${...}
            new SyntaxCase("el", "el-arith.zul", Outcome.RENDERS, "EL42END", null, null),
            new SyntaxCase("el", "el-empty.zul", Outcome.RENDERS, "ELEMPTYtrue", null, null),
            new SyntaxCase("el", "el-if.zul", Outcome.RENDERS, "ELIFSHOWN", null, null),

            // P3.1 -- if / unless
            new SyntaxCase("if-unless", "if-true.zul", Outcome.RENDERS, "IFTRUE", null, null),
            new SyntaxCase("if-unless", "if-false.zul", Outcome.PLACEHOLDER, "alwayshere", "IFFALSE", null),
            new SyntaxCase("if-unless", "unless-true.zul", Outcome.PLACEHOLDER, "alwayshere", "UNLESSGONE", null),

            // P3.1 -- forEach
            new SyntaxCase("foreach", "foreach-commalist.zul", Outcome.RENDERS, "ITEMCC", null, null),
            new SyntaxCase("foreach", "foreach-status.zul", Outcome.RENDERS, "IDX1", null, null),
            new SyntaxCase("foreach", "foreach-zscript.zul", Outcome.RENDERS, "ITERFEB", null, null),

            // P3.1 -- <zk> container
            new SyntaxCase("zk-container", "zk-group.zul", Outcome.RENDERS, "ZKGROUPTWO", null, null),
            new SyntaxCase("zk-container", "zk-if.zul", Outcome.PLACEHOLDER, "alwayshere", "ZKHIDDEN", null),
            new SyntaxCase("zk-container", "zk-foreach.zul", Outcome.RENDERS, "ZKQ", null, null),

            // P3.2 -- namespaces
            new SyntaxCase("namespaces", "ns-default.zul", Outcome.RENDERS, "NSDEFAULT", null, null),
            // negative proof: zk:if="false" must hide the label; if the zk prefix were ignored it would leak.
            new SyntaxCase("namespaces", "ns-zk.zul", Outcome.PLACEHOLDER, "alwayshere", "NSZKHIDDEN", null),
            new SyntaxCase("namespaces", "ns-shortcut.zul", Outcome.RENDERS, "NSSHORTCUT", null, null),

            // P3.2 -- native HTML passthrough
            new SyntaxCase("native", "native-table.zul", Outcome.RENDERS, "NATIVECELL", null, null),
            new SyntaxCase("native", "native-with-zk-child.zul", Outcome.RENDERS, "NATIVECHILD", null, null),
            new SyntaxCase("native", "native-attr.zul", Outcome.RENDERS, "NATIVEATTRVAL", null, null),

            // P3.2 -- client namespace attributes. Markers live INSIDE the client-listener body, so they
            // only appear if the w: listener was actually emitted to the client (not if silently dropped).
            new SyntaxCase("client", "client-onfocus.zul", Outcome.RENDERS, "CLIENTFOCUSMARK", null, null),
            new SyntaxCase("client", "client-attribute.zul", Outcome.RENDERS, "CLIENTATTRVAL", null, null),
            new SyntaxCase("client", "client-shortcut.zul", Outcome.RENDERS, "CLIENTSHORTCUTMARK", null, null),

            // P3.2 -- <zscript>
            new SyntaxCase("zscript", "zscript-inline.zul", Outcome.RENDERS, "ZSCRIPTLABEL", null, null),
            new SyntaxCase("zscript", "zscript-guarded.zul", Outcome.PLACEHOLDER, "alwayshere", "ZSGUARDED", null),
            // discovery: a missing external zscript src -- hypothesis GRACEFUL_FAIL naming the path.
            new SyntaxCase("zscript", "zscript-external-missing.zul", Outcome.GRACEFUL_FAIL, "nosuchscript", null, null),

            // P3.3 -- processing instructions (directives)
            new SyntaxCase("pi", "pi-page-title.zul", Outcome.RENDERS, "PITITLE", null, null),
            new SyntaxCase("pi", "pi-component.zul", Outcome.RENDERS, "PICOMPONENT", null, null),
            // discovery: a directive naming a missing class -- hypothesis GRACEFUL_FAIL naming it.
            new SyntaxCase("pi", "pi-missing.zul", Outcome.GRACEFUL_FAIL, "NoSuchResolver", null, null),

            // P3.3 -- <custom-attributes> (read back via EL)
            new SyntaxCase("custom-attributes", "ca-component.zul", Outcome.RENDERS, "CAVALUE", null, null),
            new SyntaxCase("custom-attributes", "ca-page-scope.zul", Outcome.RENDERS, "CAPAGE", null, null),
            new SyntaxCase("custom-attributes", "ca-composite-map.zul", Outcome.RENDERS, "CAMAPVAL", null, null),

            // P3.3 -- <variables> (read back via EL)
            new SyntaxCase("variables", "var-basic.zul", Outcome.RENDERS, "VARBASIC", null, null),
            new SyntaxCase("variables", "var-local.zul", Outcome.RENDERS, "VARLOCAL", null, null),
            new SyntaxCase("variables", "var-composite-list.zul", Outcome.RENDERS, "VARONE", null, null),

            // P3.3 -- MVVM annotations (established M-1 placeholder: expr text present, real value absent)
            new SyntaxCase("mvvm", "mvvm-load.zul", Outcome.PLACEHOLDER, "vm.greeting", "LOADED", null),
            new SyntaxCase("mvvm", "mvvm-bind.zul", Outcome.PLACEHOLDER, "vm.name", "LOADED", null),
            new SyntaxCase("mvvm", "mvvm-init-command.zul", Outcome.PLACEHOLDER, "vm.greeting", "LOADED", null),

            // P3.3 -- annotations / <template>
            new SyntaxCase("annotations-template", "tmpl-model.zul", Outcome.PLACEHOLDER, "each.name", "LOADED", null),
            new SyntaxCase("annotations-template", "tmpl-page-level.zul", Outcome.PLACEHOLDER, "alwayshere", "TMPLINERT", null),
            new SyntaxCase("annotations-template", "annot-namespace.zul", Outcome.RENDERS, "thisIsValueNotAnnot", null, null)
    );

    /** Cartesian product: every case x both servlet variants. */
    static Stream<Arguments> corpus() {
        return CASES.stream().flatMap(c ->
                Variants.both().map(v -> Arguments.of(c, v)));
    }

    @ParameterizedTest(name = "{0} [{1}]")
    @MethodSource("corpus")
    void syntaxCase(SyntaxCase c, Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        try (RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, null)) {
            RenderResult r = engine.renderZul("/syntax/" + c.fixture());
            switch (c.outcome()) {
                case RENDERS, PLACEHOLDER -> {
                    assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describeFailure(r));
                    if (c.present() != null) {
                        assertTrue(r.getHtml().contains(c.present()),
                                () -> "expected present marker '" + c.present() + "' in: " + r.getHtml());
                    }
                    if (c.absent() != null) {
                        assertFalse(r.getHtml().contains(c.absent()),
                                () -> "absent marker '" + c.absent() + "' must NOT appear in: " + r.getHtml());
                    }
                }
                case GRACEFUL_FAIL -> {
                    assertFalse(r.isSuccess(), () -> "expected a graceful failure, but render succeeded: " + r.getHtml());
                    RenderError error = r.getError();
                    assertNotNull(error.getMessage());
                    assertTrue(error.getMessage().contains(c.present()),
                            () -> "error message must contain '" + c.present() + "': " + error.toJson());
                    if (c.phase() != null) {
                        assertEquals(c.phase(), error.getPhase(), () -> "phase mismatch: " + error.toJson());
                    }
                }
            }
        }
    }

    private static String describeFailure(RenderResult r) {
        return r.isSuccess() ? "(success)" : r.getError().toJson();
    }
}
