package org.zkoss.zkpreview;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.zkoss.zkpreview.testutil.Variants;
import org.zkoss.zkpreview.testutil.ZkClasspathResolver;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression lock for the {@code ~./} user-resource fix (root cause: the plugin handed the
 * launcher a jars-only classpath, so a user's own {@code ~./} {@code ClassWebResource} pages
 * -- which live in a resource <em>directory</em> -- were unreachable). ZK resolves {@code ~./x}
 * from the classpath at {@code /web/x}, so the fix is to also pass the module's resource roots
 * (directories) on the render classpath.
 *
 * <p>This proves the mechanism the plugin fix relies on, end to end through the real render
 * engine: the {@code ~./} page's name exists ONLY inside a temp directory created at runtime
 * (never in the docroot, never in {@code src/test/resources}), so it can resolve <em>only</em>
 * because that directory is on the render classpath.
 */
class ClasspathResourceResolutionTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");

    static Stream<Variants.Named> variants() {
        return Variants.both();
    }

    @ParameterizedTest(name = "~./ resolves when its resource root is on the classpath [{0}]")
    @MethodSource("variants")
    void classpathResourceRoot_makesUserTildeDotResolve(Variants.Named variant, @TempDir Path cpRoot)
            throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        // web/cp-only-fragment.zul, reachable as ~./cp-only-fragment.zul -- only via this dir.
        Path web = Files.createDirectories(cpRoot.resolve("web"));
        // Marker has no hyphen on purpose: ZK 10 (jakarta) JS-escapes '-' as '\-' in the
        // rendered widget value ('CP\-ONLY'), so a hyphenated marker would false-negative the
        // contains() check on that variant while passing on javax. Keep it alphanumeric.
        Files.writeString(web.resolve("cp-only-fragment.zul"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<zk xmlns=\"http://www.zkoss.org/2005/zul\">\n"
                        + "    <label value=\"CPONLY FRAGMENT CONTENT\"/>\n"
                        + "</zk>\n");

        // WITH the resource root on the classpath -> the applied ~./ content renders.
        List<File> withRoot = new ArrayList<>(res.jars);
        withRoot.add(cpRoot.toFile());
        try (RenderEngine engine = RenderEngineFactory.create(withRoot, FIXTURES, null)) {
            RenderResult r = engine.renderZul("/apply-cp-resource.zul");
            assertTrue(r.isSuccess(), () -> "expected SUCCESS with the resource root on the classpath, got: "
                    + (r.isSuccess() ? "" : r.getError().toJson()));
            assertTrue(r.getHtml().contains("CPONLY FRAGMENT CONTENT"),
                    () -> "the ~./ resource's content must render: " + r.getHtml());
        }

        // WITHOUT it (jars only, the pre-fix launcher classpath) -> ~./ is unresolvable.
        try (RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, null)) {
            RenderResult r = engine.renderZul("/apply-cp-resource.zul");
            assertFalse(r.isSuccess(),
                    "without the resource root on the classpath, ~./ must NOT resolve (this is the bug)");
            assertTrue(r.getError().getMessage().contains("cp-only-fragment.zul"),
                    () -> "failure must name the unresolved ~./ page: " + r.getError().getMessage());
        }
    }
}
