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
 * P2 follow-up: {@code <apply>}/{@code <include>} must resolve their target across the
 * three path forms a real ZUL uses, not just an absolute {@code /docroot} path — because
 * each resolves through a different mechanism:
 *
 * <ul>
 *   <li><b>absolute</b> {@code /foo.zul} — servlet-context (docroot) lookup (covered by
 *       {@code ApplyTemplateUriTest}/{@code IncludeTest});</li>
 *   <li><b>relative</b> {@code ../foo.zul} — resolved relative to the including page's URI,
 *       then a servlet-context lookup;</li>
 *   <li><b>{@code ~./foo.zul}</b> — ZK {@code ClassWebResource}, resolved from the
 *       <em>classpath</em> {@code /web/}, never the docroot.</li>
 * </ul>
 *
 * <p>The {@code ~./} fixture is served from {@code src/test/resources/web/} so it is on the
 * render classloader's classpath (reachable exactly as a bundled {@code ~./} resource would
 * be in a packaged ZK library). This proves ZK's {@code ~./} <em>mechanism</em> given a
 * classpath resource, which covers {@code ~./} resources bundled in a jar (ZK's own
 * components, addon libs).
 *
 * <p>A user project's own {@code ~./} pages live in a resource <em>directory</em>, not a jar,
 * and {@code ZkClasspathFilter.filterLibraryJars} keeps only regular files — so they were
 * originally never passed to the launcher and failed with "Page not found" in the preview
 * while rendering fine under a real container. The plugin therefore also hands the module's
 * <em>resource roots</em> to the render classpath ({@code ZkClasspathFilter.filterResourceRoots});
 * the module <em>output</em> directory stays excluded, so class isolation is unaffected (a
 * resource root holds no compiled user classes). That production path has its own coverage in
 * {@code ClasspathResourceResolutionTest}; this class stays on the ZK mechanism itself.
 */
class PathResolutionTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");

    static Stream<Variants.Named> variants() {
        return Variants.both();
    }

    @ParameterizedTest(name = "apply ../relative resolves [{0}]")
    @MethodSource("variants")
    void applyRelativeTemplateUri_resolvesAgainstIncludingPage(Variants.Named variant) throws Exception {
        RenderResult r = render(variant, "/sub/apply-relative.zul");
        assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describeFailure(r));
        assertTrue(r.getHtml().contains("APPLIED TEMPLATE CONTENT"),
                () -> "relative templateURI must apply the target's content: " + r.getHtml());
    }

    @ParameterizedTest(name = "include ../relative resolves [{0}]")
    @MethodSource("variants")
    void includeRelativeSrc_resolvesAgainstIncludingPage(Variants.Named variant) throws Exception {
        RenderResult r = render(variant, "/sub/include-relative.zul");
        assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describeFailure(r));
        assertTrue(r.getHtml().contains("INCLUDED FRAGMENT CONTENT"),
                () -> "relative src must include the target's content: " + r.getHtml());
    }

    @ParameterizedTest(name = "apply ~./classpath resolves [{0}]")
    @MethodSource("variants")
    void applyClasspathTemplateUri_resolvesFromClasspathWeb(Variants.Named variant) throws Exception {
        RenderResult r = render(variant, "/apply-classweb.zul");
        assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describeFailure(r));
        assertTrue(r.getHtml().contains("CLASSWEB FRAGMENT CONTENT"),
                () -> "~./ templateURI must apply the classpath resource's content: " + r.getHtml());
    }

    @ParameterizedTest(name = "include ~./classpath resolves [{0}]")
    @MethodSource("variants")
    void includeClasspathSrc_resolvesFromClasspathWeb(Variants.Named variant) throws Exception {
        RenderResult r = render(variant, "/include-classweb.zul");
        assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describeFailure(r));
        assertTrue(r.getHtml().contains("CLASSWEB FRAGMENT CONTENT"),
                () -> "~./ src must include the classpath resource's content: " + r.getHtml());
    }

    private static RenderResult render(Variants.Named variant, String zulPath) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);
        try (RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, null)) {
            return engine.renderZul(zulPath);
        }
    }

    private static String describeFailure(RenderResult r) {
        return r.isSuccess() ? "(success)" : r.getError().toJson();
    }
}
