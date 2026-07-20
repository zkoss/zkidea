package org.zkoss.zkpreview;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.zkoss.zkpreview.testutil.Variants;
import org.zkoss.zkpreview.testutil.ZkClasspathResolver;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AC-6 / E1-G5: fixture (f) (zscript referencing a nonexistent class) produces a
 * structured, machine-readable failure -- not a raw stack trace -- whose message
 * contains the missing FQCN, with a zul location when the failing layer reports one.
 */
class StructuredFailureTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");

    static Stream<Variants.Named> variants() {
        return Variants.both();
    }

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("variants")
    void fixtureF_zscriptMissingClassProducesStructuredFailure(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        ForbiddenLoadTracker tracker = new ForbiddenLoadTracker(
                List.of("org.zkoss.zkpreview.testcanary."));
        try (RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, tracker)) {
            RenderResult r = engine.renderZul("/zscript-missing-class.zul");

            assertFalse(r.isSuccess(), "fixture (f) must fail to render");
            RenderError error = r.getError();
            assertNotNull(error.getPhase());
            assertNotNull(error.getMessage());
            assertFalse(error.getMessage().isBlank());
            assertTrue(error.getMessage().contains("org.zkoss.zkpreview.testcanary.CanaryZscriptTarget"),
                    "message must name the missing FQCN: " + error.getMessage());
            assertEquals("/zscript-missing-class.zul", error.getZulFile());
            assertNotNull(error.getLine(), "line should be available for a zscript-time failure");

            // Machine-readable: valid enough JSON shape (not merely a stack trace dump).
            String json = r.toJson();
            assertTrue(json.startsWith("{\"status\":\"FAILURE\""), json);
            assertTrue(json.contains("\"phase\":\"" + error.getPhase() + "\""), json);
            assertTrue(json.contains("CanaryZscriptTarget"), json);
        }
    }
}
