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
 * E3 round 3 / D3: annotation-valued shadow-element attributes (e.g.
 * {@code <apply templateURI="@load(...)">}) must not be treated as literal page
 * paths under the preview's no-op composer -- real ZK leaves the {@code <apply>}
 * uneffective (it contributes nothing) when its bound value is never resolved,
 * and the rest of the page must still render. A genuinely nonexistent literal
 * path must still fail (not over-suppressed).
 */
class ApplyTemplateUriTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");

    static Stream<Variants.Named> variants() {
        return Variants.both();
    }

    @ParameterizedTest(name = "(g) apply-templateuri-annotation.zul [{0}]")
    @MethodSource("variants")
    void fixtureG_annotationValuedTemplateUriDoesNotLeakAsLiteralPath(Variants.Named variant) throws Exception {
        RenderResult r = render(variant, "apply-templateuri-annotation.zul");
        assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describeFailure(r));
        String html = r.getHtml();
        assertTrue(html.contains("zul.wnd.Window"), html);
        assertTrue(html.contains("zul.wgt.Label"), html);
        assertTrue(html.contains("apply marker label"), html);
    }

    @ParameterizedTest(name = "(g-neg) apply-templateuri-missing.zul [{0}]")
    @MethodSource("variants")
    void fixtureGNeg_genuinelyMissingLiteralPathStillFails(Variants.Named variant) throws Exception {
        RenderResult r = render(variant, "apply-templateuri-missing.zul");
        assertFalse(r.isSuccess(), "a genuinely nonexistent literal templateURI must still fail");
        RenderError error = r.getError();
        assertNotNull(error.getMessage());
        assertTrue(error.getMessage().contains("/no/such/file.zul"),
                "message must name the unresolved literal path: " + error.getMessage());
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
