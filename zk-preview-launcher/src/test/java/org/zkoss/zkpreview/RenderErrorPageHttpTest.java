package org.zkoss.zkpreview;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.zkoss.zkpreview.testutil.Variants;
import org.zkoss.zkpreview.testutil.ZkClasspathResolver;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L-10 wire-level lock: when a {@code .zul} fails to render, the launcher must serve a
 * formatted HTML error page — never the raw {@code application/json} body that the JCEF
 * browser used to paint verbatim (the "Unexpected token"/raw-JSON trust-killer). The
 * structured {@link RenderError}/{@code toJson()} contract is unaffected (object-level,
 * see {@link StructuredFailureTest}); this only covers what the browser receives.
 */
class RenderErrorPageHttpTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");

    static Stream<Variants.Named> variants() {
        return Variants.both();
    }

    @ParameterizedTest(name = "failed render serves an HTML error page, not JSON [{0}]")
    @MethodSource("variants")
    void failedRenderServesHtmlErrorPageNotJson(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        // Forbid the canary prefix so the zscript's class genuinely can't load -- this is
        // how the harness simulates a real broken render (a missing/uncompiled user class),
        // mirroring StructuredFailureTest. Without it the canary loads and the page renders.
        ForbiddenLoadTracker tracker = new ForbiddenLoadTracker(List.of("org.zkoss.zkpreview.testcanary."));
        RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, tracker);
        PreviewHttpServer server = new PreviewHttpServer(engine, 0);
        server.start();
        try {
            int port = server.getPort();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + port + "/zscript-missing-class.zul"))
                            .timeout(Duration.ofSeconds(30))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            String body = resp.body().trim();
            String contentType = resp.headers().firstValue("Content-Type").orElse("");

            assertTrue(contentType.toLowerCase().contains("text/html"),
                    () -> "a failed render must be served as HTML, not JSON: Content-Type=" + contentType);
            assertFalse(contentType.toLowerCase().contains("application/json"),
                    () -> "a failed render must not be served as application/json (L-10): " + contentType);
            // Regression guard: the browser must not receive raw JSON to paint.
            assertFalse(body.startsWith("{"),
                    () -> "error body must be HTML, not the raw JSON envelope: " + body);
            assertTrue(body.startsWith("<"), () -> "error body must be an HTML document: " + body);
            // The formatted page must still surface the useful diagnostics.
            assertTrue(body.contains("COMPOSE"), () -> "error page must show the phase: " + body);
            assertTrue(body.contains("CanaryZscriptTarget"),
                    () -> "error page must name the missing class from the failure message: " + body);
        } finally {
            server.stop();
            engine.close();
        }
    }
}
