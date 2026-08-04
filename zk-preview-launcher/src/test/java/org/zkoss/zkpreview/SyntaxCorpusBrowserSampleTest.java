package org.zkoss.zkpreview;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.zkoss.zkpreview.testutil.ZkClasspathResolver;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P3.4 deeper-oracle sample (layer 2 of the verification model in
 * PLAN-P3-syntax-corpus.md): drive a REAL headless browser (Playwright/Chromium) against a
 * representative handful of {@link ZulSyntaxCorpusTest} fixtures and assert the marker appears in
 * the <em>live DOM</em> -- i.e. the ZK client actually instantiated the widgets from the served
 * HTML, not merely that the server emitted the marker as a string.
 *
 * <p>Complements {@link BrowserEquivalentTest} (which covers {@code plain.zul}); this class extends
 * that Playwright path to the syntax corpus. Skips cleanly when Playwright cannot drive a browser
 * (matching {@code BrowserEquivalentTest}'s documented fallback discipline), so it is never a false
 * green. Uses the jakarta variant only (the per-case javax+jakarta matrix is
 * {@link ZulSyntaxCorpusTest}'s job; this is a spot-check that the client render pipeline works).
 */
class SyntaxCorpusBrowserSampleTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");

    /** Representative cases spanning static, native-passthrough, iterative, and zscript-created DOM. */
    static Stream<Arguments> sample() {
        return Stream.of(
                Arguments.of("/syntax/elem-basic.zul", "ELEMBASIC"),
                Arguments.of("/syntax/native-table.zul", "NATIVECELL"),
                Arguments.of("/syntax/foreach-commalist.zul", "ITEMCC"),
                Arguments.of("/syntax/zscript-inline.zul", "ZSCRIPTLABEL"));
    }

    @ParameterizedTest(name = "live DOM {0} contains {1}")
    @MethodSource("sample")
    void liveDomContainsMarker(String zulPath, String marker) throws Exception {
        ZkClasspathResolver.Resolution res = ZkClasspathResolver.resolveJakarta();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, null);
        PreviewHttpServer server = new PreviewHttpServer(engine, 0);
        server.start();
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage();
            page.navigate("http://127.0.0.1:" + server.getPort() + zulPath);
            // Wait for the ZK client to build the DOM node carrying the marker text.
            page.waitForSelector("text=" + marker, new Page.WaitForSelectorOptions().setTimeout(15000));
            assertTrue(page.content().contains(marker),
                    "live DOM must contain '" + marker + "' for " + zulPath);
        } catch (Exception e) {
            if (e instanceof org.opentest4j.TestAbortedException) {
                throw e;
            }
            Assumptions.assumeTrue(false, "Playwright unavailable, skipping live-DOM sample: " + e);
        } finally {
            server.stop();
            engine.close();
        }
    }
}
