package org.zkoss.zkpreview;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.zkoss.zkpreview.testutil.AddonMatrix;
import org.zkoss.zkpreview.testutil.ZkClasspathResolver;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Renders each ZK add-on (Charts, Calendar, Pivottable, Keikai) against the ZK core it is
 * paired with in {@link AddonMatrix}, through the real render engine.
 *
 * <p>Every other render test in this suite runs on ZK core alone. That gap is why the zkcharts
 * NPE shipped: zkcharts' {@code WebAppInit} stores a null license code through
 * {@code WebApp.setAttribute}, the mock context put it into a {@code ConcurrentHashMap}, and the
 * launcher died before binding a port -- so any project with zkcharts on its classpath simply
 * could not preview. Six of the rows below would have caught it at the bootstrap assertion.
 *
 * <p>Three assertions per row, each locking a distinct failure mode:
 * <ol>
 *   <li><b>renders</b> -- the add-on's {@code WebAppInit} survives the mock container and its
 *       {@code lang-addon.xml} is discovered through the scoped classloader (otherwise the
 *       element is simply unknown);</li>
 *   <li><b>widget class in the HTML</b> -- the component resolved to the add-on's own widget,
 *       not to a same-named core component;</li>
 *   <li><b>the add-on's WPD serves a non-empty 200</b> -- the "renders unstyled" mode, which a
 *       render-only assertion cannot see: {@code AbstractRenderEngine} logs a resource failure
 *       but collapses it to a 404 the page never reports.</li>
 * </ol>
 *
 * <p>Resolution-gated with {@link Assumptions} exactly like {@code RealWorldSmokeTest}: the
 * Charts, Pivottable and Keikai rows need EE credentials in {@code ~/.m2/settings.xml}, so a
 * machine without them (CI) skips with a printed reason instead of false-failing. Only the two
 * Calendar rows resolve from the free repo.
 */
@Tag("addons")
class AddonRenderMatrixTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");

    /** ZK serves its JS/CSS under a per-build hash segment, only knowable from the page. */
    private static final Pattern BUILD_HASH = Pattern.compile("/zkau/web/([^/\"]+)/");

    static Stream<AddonMatrix.Row> matrix() {
        return AddonMatrix.rows();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrix")
    void addonRendersItsWidgetAndServesItsOwnAssets(AddonMatrix.Row row) throws Exception {
        ZkClasspathResolver.Resolution res = row.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        try (RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, null)) {
            RenderResult r = engine.renderZul(row.fixture);
            assertTrue(r.isSuccess(), () -> row.fixture + " must render on " + row.id + ": "
                    + (r.isSuccess() ? "" : r.getError().toJson()));

            String html = r.getHtml();
            assertTrue(html.contains(row.widgetClass),
                    () -> "expected the add-on's widget class " + row.widgetClass + " in the page: " + html);

            Matcher hash = BUILD_HASH.matcher(html);
            assertTrue(hash.find(), () -> "expected a /zkau/web/<build-hash>/ URL in the page: " + html);
            String wpd = "/web/" + hash.group(1) + "/js/" + row.widgetPackage + ".wpd";

            ResourceResult asset = engine.resource(wpd);
            assertEquals(200, asset.getStatus(),
                    () -> "the add-on's own JS module must serve, else the preview renders unstyled: " + wpd);
            assertNotNull(asset.getBody(), () -> "200 with no body: " + wpd);
            assertTrue(asset.getBody().length > 0, () -> "200 with an empty body: " + wpd);
        }
    }
}
