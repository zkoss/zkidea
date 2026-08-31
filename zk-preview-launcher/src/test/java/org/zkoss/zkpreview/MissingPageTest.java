package org.zkoss.zkpreview;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A {@code .zul} path with no file behind it must be answered {@code 404}, not {@code 200}.
 *
 * <p>It was answered {@code 200} with a zero-byte body, so no caller could tell a mistyped path
 * from a page that rendered blank (zkoss/zkidea#71). ZK reported the miss all along: its
 * {@code DHtmlLayoutServlet} cannot obtain a desktop for a nonexistent page and calls
 * {@code sendError} on the response, which the launcher's mock response records -- and
 * {@code AbstractRenderEngine.renderOnce} then returned {@code RenderResult.success(...)}
 * unconditionally, never reading {@code resp.getStatus()}. The status was captured and discarded.
 *
 * <p>Why {@code 404} specifically, and not the {@code 500} error page a broken ZUL gets: a missing
 * page is not a render failure. Downstream, {@code preview-zul.py} scrapes an error page only for
 * {@code status >= 500} and otherwise reports {@code the render server answered HTTP <status>} with
 * the hint {@code check that the .zul path is correct relative to the docroot} -- written for
 * exactly this case, and unreachable while the server answered {@code 200}. A {@code 500} here
 * would send the reader looking for a fault in a file that does not exist.
 *
 * <p>The third state is {@code notServed} rather than a {@link RenderError} failure for the same
 * reason: an error carries a phase, a message and a stack trace, and there is no fault to describe.
 * The controls below pin both neighbours -- a page that renders is still {@code 200}, and a page
 * that genuinely fails is still a {@code 500} error page with its {@code RenderError} intact.
 */
class MissingPageTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");

    static Stream<Variants.Named> variants() {
        return Variants.both();
    }

    // ------------------------------------------------------- engine level, against real ZK

    @ParameterizedTest(name = "a missing .zul is not-served with 404 [{0}]")
    @MethodSource("variants")
    void missingZulIsNotServedWith404(Variants.Named variant) throws Exception {
        RenderResult r = render(variant, "/no-such-page.zul");

        assertTrue(r.isNotServed(), () -> "a nonexistent page must be not-served: " + describe(r));
        assertEquals(404, r.getNotServedStatus(), "ZK's own sendError status must be carried through");
        assertFalse(r.isSuccess(), "a page that does not exist was not rendered");
        assertNull(r.getError(),
                "not-served is not a render failure: there is no phase, message or trace to report");
    }

    @ParameterizedTest(name = "a missing .zul deep in the tree is 404 too [{0}]")
    @MethodSource("variants")
    void missingZulAtDepthIsAlsoNotServed(Variants.Named variant) throws Exception {
        RenderResult r = render(variant, "/deeply/nested/no-such-page.zul");
        assertTrue(r.isNotServed(), () -> "depth must not change the answer: " + describe(r));
        assertEquals(404, r.getNotServedStatus());
    }

    @ParameterizedTest(name = "the reason names the path [{0}]")
    @MethodSource("variants")
    void notServedReasonNamesTheRequestedPath(Variants.Named variant) throws Exception {
        RenderResult r = render(variant, "/no-such-page.zul");
        assertNotNull(r.getNotServedReason(), "a not-served result must say why, for the log and the body");
        assertTrue(r.getNotServedReason().contains("/no-such-page.zul"),
                () -> "the reason must name the path that was not found: " + r.getNotServedReason());
    }

    /** Control: the status check must not turn a page that renders into a miss. */
    @ParameterizedTest(name = "an existing .zul still renders [{0}]")
    @MethodSource("variants")
    void existingZulStillRendersSuccessfully(Variants.Named variant) throws Exception {
        RenderResult r = render(variant, "/plain.zul");
        assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describe(r));
        assertFalse(r.isNotServed(), "a rendered page is not a miss");
        assertNotNull(r.getHtml());
        assertFalse(r.getHtml().isEmpty(), "a successful render must carry its HTML");
    }

    /** Control: a page that exists and genuinely fails must stay a failure, with its error intact. */
    @ParameterizedTest(name = "a broken .zul is still a failure, not a miss [{0}]")
    @MethodSource("variants")
    void brokenZulIsStillAFailureNotAMiss(Variants.Named variant) throws Exception {
        RenderResult r = render(variant, "/unsupported-parent.zul");
        assertFalse(r.isSuccess(), "the fixture is a genuine render failure");
        assertFalse(r.isNotServed(),
                () -> "a file that exists and fails must not be reported as missing: " + describe(r));
        assertNotNull(r.getError(), "a failure must keep its structured RenderError");
    }

    // ------------------------------------------------------- HTTP level

    @Test
    void missingZulOverHttpIs404NotAnEmpty200(@TempDir Path docroot) throws Exception {
        withServer(docroot, base -> {
            HttpResponse<byte[]> r = get(base + "/no-such-page.zul");
            assertEquals(404, r.statusCode(),
                    "a .zul with no file behind it must be 404; 200 hides a mistyped path");
            return null;
        });
    }

    @Test
    void the404BodyNamesThePathAndTheDocroot(@TempDir Path docroot) throws Exception {
        withServer(docroot, base -> {
            HttpResponse<byte[]> r = get(base + "/no-such-page.zul");
            String body = new String(r.body(), StandardCharsets.UTF_8);
            assertFalse(body.isEmpty(),
                    "an empty body is what made this indistinguishable from a blank render");
            assertTrue(body.contains("/no-such-page.zul"),
                    () -> "the body must name the path that was requested: " + body);
            assertTrue(body.contains(docroot.toString()),
                    () -> "the body must name the docroot it was looked for in: " + body);
            assertTrue(header(r, "content-type").startsWith("text/plain"),
                    () -> "the body is a diagnostic line, not a page: " + header(r, "content-type"));
            return null;
        });
    }

    @Test
    void the404IsStillNeverCachedAndStillReportsControllers(@TempDir Path docroot) throws Exception {
        withServer(docroot, base -> {
            HttpResponse<byte[]> r = get(base + "/no-such-page.zul");
            assertEquals("no-store, no-cache, must-revalidate", header(r, "cache-control"),
                    "a miss must not be cached: the file may appear a keystroke later");
            assertEquals("skipped", header(r, "x-zk-preview-controllers"),
                    "no page means no controllers ran, and the header must still say so");
            return null;
        });
    }

    /** Any error status ZK sets travels as itself, rather than becoming a silent 200. */
    @ParameterizedTest(name = "not-served {0} is sent as {0}")
    @ValueSource(ints = {404, 403, 503})
    void anyNotServedStatusIsSentAsItself(int status, @TempDir Path docroot) throws Exception {
        withServer(docroot, new StubEngine(RenderResult.notServed(status, "stub says " + status)),
                base -> {
                    HttpResponse<byte[]> r = get(base + "/whatever.zul");
                    assertEquals(status, r.statusCode());
                    assertTrue(new String(r.body(), StandardCharsets.UTF_8).contains("stub says " + status),
                            "the engine's reason must reach the reader");
                    return null;
                });
    }

    /** Control: the two neighbouring routes at the HTTP layer are untouched. */
    @Test
    void aRenderedPageIsStill200Html(@TempDir Path docroot) throws Exception {
        withServer(docroot, base -> {
            HttpResponse<byte[]> r = get(base + "/plain.zul");
            assertEquals(200, r.statusCode());
            assertTrue(header(r, "content-type").startsWith("text/html"));
            assertTrue(new String(r.body(), StandardCharsets.UTF_8).contains(StubEngine.RENDERED_MARKER));
            return null;
        });
    }

    @Test
    void aFailedRenderIsStill500AnErrorPage(@TempDir Path docroot) throws Exception {
        RenderResult failure = RenderResult.failure(
                new RenderError(RenderPhase.PARSE, "STUB-PARSE-FAILURE", "/broken.zul", 3, 1));
        withServer(docroot, new StubEngine(failure), base -> {
            HttpResponse<byte[]> r = get(base + "/broken.zul");
            assertEquals(500, r.statusCode(), "a genuine failure keeps its 500");
            assertTrue(header(r, "content-type").startsWith("text/html"),
                    "a failure keeps the readable HTML error page");
            assertTrue(new String(r.body(), StandardCharsets.UTF_8).contains("STUB-PARSE-FAILURE"));
            return null;
        });
    }

    // ------------------------------------------------------- the value type

    @Test
    void toJsonReportsTheThirdStateInsteadOfDereferencingANullError() {
        assertEquals("{\"status\":\"NOT_SERVED\",\"httpStatus\":404}",
                RenderResult.notServed(404, "no such page: /x.zul").toJson());
    }

    @Test
    void notServedRefusesAStatusThatIsNotAnError() {
        assertThrows(IllegalArgumentException.class, () -> RenderResult.notServed(200, "reason"),
                "a 200 is a served page; the state would then be unrepresentable as an error");
        assertThrows(IllegalArgumentException.class, () -> RenderResult.notServed(404, ""),
                "the reason is the only thing the reader gets, so it cannot be empty");
    }

    @Test
    void withControllersKeepsTheNotServedState() {
        RenderResult r = RenderResult.notServed(404, "no such page: /x.zul")
                .withControllers(ControllerOutcome.SKIPPED, null);
        assertTrue(r.isNotServed(), "the copy must not silently degrade into a plain failure");
        assertEquals(404, r.getNotServedStatus());
        assertEquals("no such page: /x.zul", r.getNotServedReason());
    }

    // ------------------------------------------------------- helpers

    private static RenderResult render(Variants.Named variant, String zulPath) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);
        try (RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, null)) {
            return engine.renderZul(zulPath);
        }
    }

    /** Null-safe on every state, so a failing assertion prints a diagnosis instead of an NPE. */
    private static String describe(RenderResult r) {
        if (r.isSuccess()) {
            return "SUCCESS, " + (r.getHtml() == null ? "null html" : r.getHtml().length() + " chars of html");
        }
        if (r.isNotServed()) {
            return "NOT_SERVED " + r.getNotServedStatus() + " (" + r.getNotServedReason() + ")";
        }
        return r.getError() == null ? "FAILURE with no error" : "FAILURE " + r.getError().toJson();
    }

    private interface ServerBody<T> {
        T run(String base) throws Exception;
    }

    private static <T> T withServer(Path docroot, ServerBody<T> body) throws Exception {
        return withServer(docroot, new StubEngine(null), body);
    }

    private static <T> T withServer(Path docroot, StubEngine engine, ServerBody<T> body) throws Exception {
        PreviewHttpServer server = new PreviewHttpServer(engine, 0, null, docroot);
        server.start();
        try {
            return body.run("http://127.0.0.1:" + server.getPort());
        } finally {
            server.stop();
        }
    }

    private static HttpResponse<byte[]> get(String url) throws Exception {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build().send(
                HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(60)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String header(HttpResponse<?> r, String name) {
        return r.headers().firstValue(name).orElse(null);
    }

    /**
     * Answers whatever it was constructed with, so one harness covers all three states. A null
     * canned result means "behave like the real engine on a missing page" -- the default the
     * not-served HTTP tests use.
     */
    private static final class StubEngine implements RenderEngine {
        static final String RENDERED_MARKER = "STUB-RENDERED-PAGE";

        private final RenderResult canned;

        StubEngine(RenderResult canned) {
            this.canned = canned;
        }

        @Override
        public RenderResult renderZul(String zulPath) {
            if (canned != null) {
                return canned;
            }
            if (zulPath.contains("no-such-page")) {
                return RenderResult.notServed(404, "no such page: " + zulPath);
            }
            return RenderResult.success("<html><head></head><body>" + RENDERED_MARKER + "</body></html>");
        }

        @Override
        public RenderResult renderZul(String zulPath, Map<String, String> headers) {
            return renderZul(zulPath);
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
}
