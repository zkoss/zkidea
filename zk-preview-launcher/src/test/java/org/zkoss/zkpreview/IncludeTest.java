package org.zkoss.zkpreview;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.zkoss.zkpreview.testutil.Variants;
import org.zkoss.zkpreview.testutil.ZkClasspathResolver;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P2 (tasks/zul-preview/PLAN-followups.md): {@code <include>} coverage from zero, on both
 * servlet-API variants. ZK's {@code Include} pulls another page's content in through the
 * servlet container ({@code RequestDispatcher.include}); the preview renders headlessly
 * against mock servlet objects, so this is the test that proves the mock env can (or is
 * taught to) serve an include.
 *
 * <ul>
 *   <li><b>static</b> literal {@code src} -> the included page's content renders, host still renders;</li>
 *   <li><b>annotation-valued</b> {@code src} ({@code @load(...)}) -> unresolved under the no-op
 *       composer, so it contributes nothing and the host still renders (mirrors apply);</li>
 *   <li><b>missing</b> literal {@code src} -> graceful structured failure naming the path,
 *       not a raw stack (mirrors apply-templateuri-missing).</li>
 * </ul>
 */
class IncludeTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");

    static Stream<Variants.Named> variants() {
        return Variants.both();
    }

    @ParameterizedTest(name = "include-static.zul renders included content [{0}]")
    @MethodSource("variants")
    void staticInclude_rendersIncludedFragmentAndHost(Variants.Named variant) throws Exception {
        RenderResult r = render(variant, "include-static.zul");
        assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describeFailure(r));
        String html = r.getHtml();
        assertTrue(html.contains("include host marker"), () -> "host page must render: " + html);
        assertTrue(html.contains("INCLUDED FRAGMENT CONTENT"),
                () -> "the included page's content must render: " + html);
    }

    @ParameterizedTest(name = "include-annotation.zul is neutralized, host renders [{0}]")
    @MethodSource("variants")
    void annotationValuedSrc_isNeutralized_hostStillRenders(Variants.Named variant) throws Exception {
        RenderResult r = render(variant, "include-annotation.zul");
        assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describeFailure(r));
        assertTrue(r.getHtml().contains("include marker label"),
                () -> "host page must still render when src is an unresolved binding: " + r.getHtml());
    }

    @ParameterizedTest(name = "include-missing.zul fails gracefully [{0}]")
    @MethodSource("variants")
    void missingSrc_failsGracefullyWithStructuredError(Variants.Named variant) throws Exception {
        RenderResult r = render(variant, "include-missing.zul");
        assertFalse(r.isSuccess(), "a genuinely nonexistent literal include src must fail");
        RenderError error = r.getError();
        assertNotNull(error.getMessage());
        assertTrue(error.getMessage().contains("/no/such/include.zul"),
                () -> "message must name the unresolved include path: " + error.getMessage());
    }

    private static RenderResult render(Variants.Named variant, String fixture) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);
        try (RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, null)) {
            return engine.renderZul("/" + fixture);
        }
    }

    private static String describeFailure(RenderResult r) {
        return r.isSuccess() ? "(success)" : r.getError().toJson();
    }
}
