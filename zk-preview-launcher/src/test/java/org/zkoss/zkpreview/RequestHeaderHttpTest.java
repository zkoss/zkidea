package org.zkoss.zkpreview;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P2-8 at the wire level: the headers of the request the browser actually sent must reach the mock
 * servlet request, so ZK's server side ({@code Executions.getCurrent().getHeader(...)}, device and
 * browser resolution) sees a genuine request instead of the empty header map it saw until now.
 *
 * <p>Three angles, deliberately: the first needs no ZK at all and pins the plumbing
 * ({@code PreviewHttpServer} -> the engine's two-argument {@code renderZul}); the second proves ZK
 * itself can read a forwarded header on the default isolated path; the third repeats it with
 * controllers on, which is the only path where the render happens on the one-shot executor thread
 * created inside {@code renderZul} -- the reason the headers travel as a parameter and not as a
 * thread-local. Neither Python, a browser nor a screenshot is in the loop.
 *
 * <p>There is no test asserting the {@code /zkau/web/*} resource path gets no headers: forwarding
 * there is a proven page-breaker (see {@code PreviewHttpServer.requestHeaders}), and the unchanged
 * {@code RenderEngine.resource(String)} signature makes it structurally impossible anyway.
 */
class RequestHeaderHttpTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");
    private static final String MODE_HEADER = "X-ZK-Preview-Controllers";
    private static final String PROBE_UA = "P28-PROBE-UA";
    private static final String PROBE_LANG = "en-GB";

    static Stream<Variants.Named> variants() {
        return Variants.both();
    }

    /** A stub engine that renders nothing and only records the header map it was handed. */
    private static final class CapturingEngine implements RenderEngine {
        final AtomicReference<Map<String, String>> seen = new AtomicReference<>();

        @Override
        public RenderResult renderZul(String zulPath) {
            // Only reachable if the server stopped passing headers -- record the empty map so the
            // assertion below fails with "absent" rather than a NullPointerException.
            seen.set(Map.of());
            return RenderResult.success("<html><head></head><body>stub</body></html>");
        }

        @Override
        public RenderResult renderZul(String zulPath, Map<String, String> headers) {
            seen.set(headers);
            return RenderResult.success("<html><head></head><body>stub</body></html>");
        }

        @Override
        public ResourceResult resource(String pathInfo) {
            return ResourceResult.notFound();
        }

        @Override
        public byte[] auStub() {
            return new byte[0];
        }

        @Override
        public void close() {
        }
    }

    @Test
    void theRenderDispatchHandsTheRequestHeadersToTheEngine() throws Exception {
        CapturingEngine engine = new CapturingEngine();
        serve(engine, "/x.zul", resp -> assertEquals(200, resp.statusCode()));

        Map<String, String> seen = engine.seen.get();
        assertNotNull(seen, "the .zul branch must call the two-argument renderZul");
        assertEquals(PROBE_UA, lookup(seen, "user-agent"), "forwarded headers: " + seen);
        assertEquals(PROBE_LANG, lookup(seen, "accept-language"), "forwarded headers: " + seen);
    }

    @ParameterizedTest(name = "zscript reads the forwarded User-Agent, isolated default [{0}]")
    @MethodSource("variants")
    void zscriptReadsTheForwardedUserAgent(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        serve(RenderEngineFactory.create(res.jars, FIXTURES, null), "/request-headers.zul", resp -> {
            assertEquals(200, resp.statusCode(), resp.body());
            assertTrue(deEscaped(resp).contains("ua" + PROBE_UA),
                    "an empty header map renders a bare 'ua': " + resp.body());
        });
    }

    @ParameterizedTest(name = "the headers survive the one-shot controller thread [{0}]")
    @MethodSource("variants")
    void theHeadersSurviveTheControllerExecutorThread(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        serve(RenderEngineFactory.create(res.jars, FIXTURES, null, ControllerPolicy.of(true, 30)),
                "/request-headers.zul", resp -> {
                    assertEquals(200, resp.statusCode(), resp.body());
                    // 'executed', not 'failed': reading a header must not trip the fail-soft
                    // isolated retry (P2-8 AC-2).
                    assertEquals("executed", header(resp, MODE_HEADER));
                    assertTrue(deEscaped(resp).contains("ua" + PROBE_UA),
                            "the render runs on the one-shot executor thread here: " + resp.body());
                });
    }

    /** Runs one probe-carrying request against a real server over the given engine, then tears both down. */
    private static void serve(RenderEngine engine, String path, Assertion assertion) throws Exception {
        PreviewHttpServer server = new PreviewHttpServer(engine, 0);
        server.start();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort() + path))
                            .timeout(Duration.ofSeconds(90))
                            .header("User-Agent", PROBE_UA)
                            .header("Accept-Language", PROBE_LANG)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertion.check(resp);
        } finally {
            server.stop();
            engine.close();
        }
    }

    /**
     * Case-insensitive lookup: {@code com.sun.net.httpserver} normalises header-name capitalisation
     * on the way in ({@code User-Agent}), and the mock lowercases only inside its own
     * {@code setHeader} -- so the map the server hands the engine carries whatever casing the JDK
     * chose, exactly as the servlet API's case-insensitive contract allows.
     */
    private static String lookup(Map<String, String> headers, String name) {
        return headers.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase(Locale.ROOT).equals(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /** ZK 10 escapes '-' as '\\-' in the zkmx bootstrap's JS string literals, ZK 9 does not. */
    private static String deEscaped(HttpResponse<String> resp) {
        return resp.body().replace("\\-", "-");
    }

    private static String header(HttpResponse<String> resp, String name) {
        return resp.headers().firstValue(name).orElse(null);
    }

    private interface Assertion {
        void check(HttpResponse<String> resp);
    }
}
