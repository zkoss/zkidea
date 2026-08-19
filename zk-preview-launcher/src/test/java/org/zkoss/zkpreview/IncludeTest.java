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
 * {@code <include>} coverage on both servlet-API variants: the preview renders headlessly
 * against mock servlet objects, so these cases prove the mock environment can serve an
 * include at all.
 *
 * <p>Finding worth keeping: no mock {@code RequestDispatcher} was needed. Both mock contexts
 * still return {@code null} from {@code getRequestDispatcher(...)}, yet a static {@code .zul}
 * include renders its content — ZK resolves a {@code .zul} include as an <em>instant</em>
 * include (it loads the page definition and builds the child components inline within the same
 * execution, exactly like {@code <apply>}), so it never routes through
 * {@code RequestDispatcher.include}. That only matters for non-ZK includes (JSP/servlet),
 * which are out of scope for a ZUL layout preview.
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

    /** ZK's failure text when an include has to route through a RequestDispatcher (issue #69). */
    private static final String NO_DISPATCHER = "No dispatcher available";

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
        String html = r.getHtml();
        assertTrue(html.contains("include marker label"),
                () -> "host page must still render when src is an unresolved binding: " + html);
        // Issue #69: "the host renders" was too weak an assertion -- the include ALSO leaked the
        // binding text into src ("vm.includeSrc"), which ZK then tried to load as a path and
        // failed on, so the page rendered WITH an error box sitting inside it.
        assertFalse(html.contains(NO_DISPATCHER),
                () -> "a ViewModel-referencing src must contribute nothing, not a dispatcher failure: " + html);
        assertFalse(html.contains("vm.includeSrc"),
                () -> "the binding expression must not be written into src as if it were a path: " + html);
    }

    /**
     * Issue #69: when the bound expression is a <em>constant</em> path literal
     * ({@code src="@load('~./page.zul')"}) the path is fully known at parse time, so the
     * preview must include it for real -- that is what the real binder does. The reported
     * symptom was the opposite: the raw expression text (quotes included) was written into
     * {@code src}, so ZK looked for a page literally named {@code '~./page.zul'}, found it
     * was not a {@code .zul} instant include, and fell through to
     * {@code RequestDispatcher.include} -- which the headless preview cannot serve, leaving a
     * red "No dispatcher available to include ..." box in the middle of the page.
     */
    @ParameterizedTest(name = "include-annotation-literal.zul includes the constant path for real [{0}]")
    @MethodSource("variants")
    void annotationValuedSrc_constantLiteral_isIncludedForReal(Variants.Named variant) throws Exception {
        RenderResult r = render(variant, "include-annotation-literal.zul");
        assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describeFailure(r));
        String html = r.getHtml();
        assertTrue(html.contains("include host marker"), () -> "host page must render: " + html);
        assertFalse(html.contains(NO_DISPATCHER),
                () -> "a constant literal src must not be loaded as raw expression text: " + html);
        assertTrue(html.contains("CLASSWEB FRAGMENT CONTENT"),
                () -> "@load('~./x.zul') names a real classpath page: it must be included: " + html);
        assertTrue(html.contains("INCLUDED FRAGMENT CONTENT"),
                () -> "@load('/x.zul') names a real docroot page: it must be included: " + html);
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
