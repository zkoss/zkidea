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
 * P0-2 item 4 at the wire level: every {@code .zul} response says which controller mode produced
 * it, on success and on failure alike. This header is the contract {@code preview-zul.py} parses
 * into its {@code CONTROLLERS:} line, so it is locked here -- without Python, a browser or a
 * screenshot in the loop.
 */
class ControllerHeaderHttpTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");
    private static final String MODE_HEADER = "X-ZK-Preview-Controllers";
    private static final String FAILURE_HEADER = "X-ZK-Preview-Controller-Failure";

    static Stream<Variants.Named> variants() {
        return Variants.both();
    }

    @ParameterizedTest(name = "the isolated default reports 'skipped' [{0}]")
    @MethodSource("variants")
    void isolatedDefaultReportsSkipped(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        serve(RenderEngineFactory.create(res.jars, FIXTURES, null), "/viewmodel-bind.zul", resp -> {
            assertEquals(200, resp.statusCode());
            assertEquals("skipped", header(resp, MODE_HEADER));
            assertNull(header(resp, FAILURE_HEADER), "nothing failed, so no failure header");
        });
    }

    @ParameterizedTest(name = "controllers on reports 'executed' [{0}]")
    @MethodSource("variants")
    void controllersOnReportsExecuted(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        serve(RenderEngineFactory.create(res.jars, FIXTURES, null, ControllerPolicy.of(true, 30)),
                "/viewmodel-bind.zul", resp -> {
                    assertEquals(200, resp.statusCode());
                    assertEquals("executed", header(resp, MODE_HEADER));
                    // ZK 10 escapes '-' as '\\-' in the zkmx bootstrap's JS string literals, ZK 9
                    // does not; de-escape so one assertion covers both variants.
                    assertTrue(resp.body().replace("\\-", "-").contains("LOADED-CANARY-VALUE"), resp.body());
                });
    }

    @ParameterizedTest(name = "a failed controller reports 'failed' with HTTP 200 [{0}]")
    @MethodSource("variants")
    void failedControllerStillServesTheDegradedPageWith200(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        serve(RenderEngineFactory.create(res.jars, FIXTURES, null, ControllerPolicy.of(true, 30)),
                "/controllers-failing.zul", resp -> {
                    assertEquals(200, resp.statusCode(), "fail soft: the degraded page is a normal 200");
                    assertEquals("failed", header(resp, MODE_HEADER));
                    String failure = header(resp, FAILURE_HEADER);
                    assertNotNull(failure, "a failed render must say why");
                    assertFalse(failure.isBlank(), failure);
                    assertEquals(failure, failure.replaceAll("[\\r\\n]", ""), "header values are single-line");
                    for (int i = 0; i < failure.length(); i++) {
                        char c = failure.charAt(i);
                        assertTrue(c >= 0x20 && c <= 0x7e, "header values are printable ASCII: " + failure);
                    }
                });
    }

    @ParameterizedTest(name = "a broken .zul still 500s, with the mode reported [{0}]")
    @MethodSource("variants")
    void brokenZulStillServesTheErrorPageWithTheModeReported(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        // Same setup as RenderErrorPageHttpTest: the zscript's class genuinely cannot load, which
        // no controller mode can rescue -- so the error path must still report the mode.
        ForbiddenLoadTracker tracker = new ForbiddenLoadTracker(List.of("org.zkoss.zkpreview.testcanary."));
        serve(RenderEngineFactory.create(res.jars, FIXTURES, tracker, ControllerPolicy.of(true, 30)),
                "/zscript-missing-class.zul", resp -> {
                    assertEquals(500, resp.statusCode());
                    // The mode must be reported on the error path too -- and it must be honest:
                    // the isolated retry failed the same way, so no controller caused this and
                    // none is named (the same headers the page gets without --run-controllers).
                    assertEquals("skipped", header(resp, MODE_HEADER));
                    assertNull(header(resp, FAILURE_HEADER),
                            "a broken .zul must not be attributed to a controller");
                    assertTrue(resp.body().contains("CanaryZscriptTarget"), resp.body());
                });
    }

    /** Runs one request against a real server over the given engine, then tears both down. */
    private static void serve(RenderEngine engine, String path, Assertion assertion) throws Exception {
        PreviewHttpServer server = new PreviewHttpServer(engine, 0);
        server.start();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort() + path))
                            .timeout(Duration.ofSeconds(90))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertion.check(resp);
        } finally {
            server.stop();
            engine.close();
        }
    }

    private static String header(HttpResponse<String> resp, String name) {
        return resp.headers().firstValue(name).orElse(null);
    }

    private interface Assertion {
        void check(HttpResponse<String> resp);
    }
}
