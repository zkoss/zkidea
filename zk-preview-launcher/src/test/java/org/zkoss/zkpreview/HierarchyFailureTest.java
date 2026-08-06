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
 * A {@code UiException} thrown while ZK builds the component tree -- i.e. after the ZUML
 * document already parsed successfully -- must classify as {@link RenderPhase#COMPOSE},
 * not {@link RenderPhase#UNKNOWN} (it used to report UNKNOWN, which told the user nothing
 * about where their page broke).
 * Fixture (h) ({@code unsupported-parent.zul}, a {@code <row>} directly under
 * {@code <window>}) reproduces the exact "Unsupported parent for row" the user hit via
 * manual-test/scope-var-completion.zul's {@code <apply templateURI="/WEB-INF/template/row.zul">}
 * (that template's {@code <row>} lands directly under {@code <window>} once applied).
 */
class HierarchyFailureTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");

    static Stream<Variants.Named> variants() {
        return Variants.both();
    }

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("variants")
    void fixtureH_unsupportedParentClassifiesAsComposeNotUnknown(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        ForbiddenLoadTracker tracker = new ForbiddenLoadTracker(
                List.of("org.zkoss.zkpreview.testcanary."));
        try (RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, tracker)) {
            RenderResult r = engine.renderZul("/unsupported-parent.zul");

            assertFalse(r.isSuccess(), "a <row> directly under <window> must fail to render");
            RenderError error = r.getError();
            assertEquals(RenderPhase.COMPOSE, error.getPhase(),
                    "hierarchy UiException must classify as COMPOSE, not UNKNOWN: " + r.toJson());
            assertTrue(error.getMessage().contains("Unsupported parent for row"),
                    "message must preserve ZK's own diagnostic: " + error.getMessage());
            // Investigation finding: the real org.zkoss.zk.ui.UiException
            // thrown by Row.beforeParentChanged() carries no cause and no line/column in
            // its message -- there is no position info anywhere in the exception chain to
            // recover, so line/column legitimately stay null here (not a mapper defect).
            assertNull(error.getLine());
            assertNull(error.getColumn());
        }
    }
}
