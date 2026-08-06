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
 * A ZUL that uses an add-on component whose jar is absent from the module classpath must fail
 * with a message that says so.
 *
 * <p>Renders the very same {@code /addons/ckeditor.zul} that {@code AddonRenderMatrixTest}
 * renders successfully <em>with</em> {@code org.zkoss.zkforge:ckez} on the classpath -- here
 * against ZK core alone. The two tests are the two halves of one story: with the add-on jar the
 * preview renders {@code ckez.CKeditor}; without it, the preview must name the real problem.
 *
 * <p>What ZK raises is {@code DefinitionNotFoundException: Component definition not found:
 * ckeditor in [LanguageDefinition: xul/html]}, which reads as if the preview were broken. It is
 * not: the add-on jar simply is not there (a commented-out or unsynced dependency). The reported
 * message therefore has to point at the classpath, because that is where the fix is -- the raw
 * ZK text sends a reader looking in the wrong place.
 */
class UnknownComponentDiagnosticTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");

    static Stream<Variants.Named> variants() {
        return Variants.both();
    }

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("variants")
    void addonComponentWithoutItsJarBlamesTheClasspath(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        try (RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, null)) {
            RenderResult r = engine.renderZul("/addons/ckeditor.zul");

            assertFalse(r.isSuccess(), "ZK core alone cannot define <ckeditor>");
            RenderError error = r.getError();
            String message = error.getMessage();

            assertTrue(message.contains("<ckeditor>"),
                    () -> "message must name the unknown element: " + message);
            assertTrue(message.contains("classpath"),
                    () -> "message must point at the module classpath, where the fix is: " + message);
            assertEquals("/addons/ckeditor.zul", error.getZulFile());
            // ZK resolves component definitions inside its ZUL parser, before any composer runs,
            // so this dies at parse time -- not the COMPOSE the generic UiException branch assumes.
            assertEquals(RenderPhase.PARSE, error.getPhase());
            // The raw ZK exception stays available for the stack-trace section and the report.
            assertTrue(error.getStackTrace().contains("DefinitionNotFoundException"),
                    () -> "the underlying ZK failure must still be captured: " + error.getStackTrace());
        }
    }
}
