package org.zkoss.zkpreview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The launcher must serve regular files out of the webapp docroot it was given.
 *
 * <p>It did not. Only {@code GET *.zul} and {@code GET /zkau/*} were routed, so every other path
 * fell through to a bare 404 -- including a project's own images, stylesheets and scripts sitting
 * at exactly the requested path inside {@code --webapp}. A page with
 * {@code <image src="/img/logo.png"/>} previewed as an empty box, and a page's own CSS silently
 * never applied, which made a preview screenshot unrepresentative with no error to notice.
 *
 * <p>The cause was structural rather than a path bug: a real servlet container serves docroot
 * files through its own {@code DefaultServlet}, implicitly mapped at {@code /} and never written
 * into {@code web.xml}. The launcher runs no container, so porting "the servlets a ZK webapp
 * declares" reproduced ZK's two and none of the container's one. See
 * {@code doc/preview-launcher-architecture.md}.
 *
 * <p>Adding a file route also removes an accidental safety property: until now no request path was
 * ever resolved against the filesystem, so traversal was impossible by construction. The
 * confinement tests below are therefore load-bearing, not belt-and-braces.
 *
 * <p><b>Deliberately not asserted here:</b> that a symlink inside the docroot pointing outside it
 * is refused. It is served, and that is a withdrawn requirement (spec S3), not a gap awaiting a
 * test. A docroot-bounded {@code toRealPath()} would 404 legitimate assets -- a preview docroot is
 * a live source tree where symlinked asset folders are normal -- and previewing an untrusted
 * project already grants code execution in this JVM via {@code <zscript>}, so the read it would
 * prevent is a strict downgrade from what is already possible. Do not "fix" this by adding a
 * docroot-bounded real-path check: that breaks real previews and closes nothing.
 */
class StaticAssetServingTest {

    // ---------------------------------------------------------------- R1: serve the file

    @Test
    void servesRegularFileWithExactBytesAndContentLength(@TempDir Path docroot) throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 1, 2, 3};
        Files.createDirectories(docroot.resolve("assets"));
        Files.write(docroot.resolve("assets/logo.png"), png);

        withServer(docroot, base -> {
            HttpResponse<byte[]> r = getBytes(base + "/assets/logo.png");
            assertEquals(200, r.statusCode(), "a regular file inside the docroot must be served");
            assertArrayEquals(png, r.body(), "the exact bytes of the file must come back");
            assertEquals(String.valueOf(png.length), header(r, "content-length"));
            return null;
        });
    }

    @Test
    void servesFileAtTheDocrootRoot(@TempDir Path docroot) throws Exception {
        Files.writeString(docroot.resolve("favicon.ico"), "not really an icon");
        withServer(docroot, base -> {
            assertEquals(200, getBytes(base + "/favicon.ico").statusCode());
            return null;
        });
    }

    // ---------------------------------------------------------------- R2: Content-Type

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "a.css,        text/css",
            "a.js,         text/javascript",
            "a.mjs,        text/javascript",
            "a.json,       application/json",
            "a.png,        image/png",
            "a.jpg,        image/jpeg",
            "a.jpeg,       image/jpeg",
            "a.gif,        image/gif",
            "a.svg,        image/svg+xml",
            "a.webp,       image/webp",
            "a.ico,        image/vnd.microsoft.icon",
            "a.woff,       font/woff",
            "a.woff2,      font/woff2",
            "a.ttf,        font/ttf",
            "a.eot,        application/vnd.ms-fontobject",
            "a.txt,        text/plain",
            "a.html,       text/html",
            "a.htm,        text/html",
            "a.js.map,     application/json",
            "a.unknownext, application/octet-stream",
            "noextension,  application/octet-stream",
            "A.PNG,        image/png",
    })
    void contentTypeIsDerivedFromTheExtension(String name, String expectedType, @TempDir Path docroot)
            throws Exception {
        Files.writeString(docroot.resolve(name), "body");
        withServer(docroot, base -> {
            HttpResponse<byte[]> r = getBytes(base + "/" + name);
            assertEquals(200, r.statusCode(), name);
            String ct = header(r, "content-type");
            assertNotNull(ct, () -> "no Content-Type for " + name);
            assertTrue(ct.startsWith(expectedType),
                    () -> name + " expected " + expectedType + " but got " + ct);
            return null;
        });
    }

    @Test
    void textTypesCarryUtf8AndBinaryTypesDoNot(@TempDir Path docroot) throws Exception {
        for (String n : List.of("a.css", "a.js", "a.txt", "a.html")) {
            Files.writeString(docroot.resolve(n), "x");
        }
        for (String n : List.of("a.png", "a.woff", "a.bin")) {
            Files.writeString(docroot.resolve(n), "x");
        }
        withServer(docroot, base -> {
            for (String n : List.of("a.css", "a.js", "a.txt", "a.html")) {
                String ct = header(getBytes(base + "/" + n), "content-type");
                assertTrue(ct.contains("charset=UTF-8"), () -> n + " must declare UTF-8, got " + ct);
            }
            for (String n : List.of("a.png", "a.woff", "a.bin")) {
                String ct = header(getBytes(base + "/" + n), "content-type");
                assertFalse(ct.contains("charset"), () -> n + " must not declare a charset, got " + ct);
            }
            return null;
        });
    }

    // ---------------------------------------------------------------- R3: precedence

    @Test
    void zulIsRenderedAsAPageNeverReturnedAsSourceText(@TempDir Path docroot) throws Exception {
        String source = "<zk><label value=\"THIS IS THE RAW SOURCE\"/></zk>";
        Files.writeString(docroot.resolve("page.zul"), source);

        withServer(docroot, base -> {
            HttpResponse<byte[]> r = getBytes(base + "/page.zul");
            assertEquals(200, r.statusCode());
            String body = new String(r.body(), StandardCharsets.UTF_8);
            assertTrue(body.contains(StubEngine.RENDERED_MARKER),
                    () -> "the .zul handler must still win over the file route: " + body);
            assertFalse(body.contains("THIS IS THE RAW SOURCE"),
                    () -> "a .zul must never be served as source text: " + body);
            assertTrue(header(r, "content-type").startsWith("text/html"));
            return null;
        });
    }

    @Test
    void zkauResourceHandlerWinsOverAnIdenticallyNamedDocrootFile(@TempDir Path docroot) throws Exception {
        Files.createDirectories(docroot.resolve("zkau/web"));
        Files.writeString(docroot.resolve("zkau/web/probe.css"), "FROM THE DOCROOT");

        withServer(docroot, base -> {
            HttpResponse<byte[]> r = getBytes(base + "/zkau/web/probe.css");
            assertEquals(200, r.statusCode());
            String body = new String(r.body(), StandardCharsets.UTF_8);
            assertEquals(StubEngine.RESOURCE_BODY, body,
                    "the classpath resource handler must keep precedence over the docroot");
            return null;
        });
    }

    // ---------------------------------------------------------------- R4: methods

    @Test
    void headReturnsTheSameStatusAndLengthWithNoBody(@TempDir Path docroot) throws Exception {
        byte[] body = "0123456789".getBytes(StandardCharsets.UTF_8);
        Files.write(docroot.resolve("a.txt"), body);

        withServer(docroot, base -> {
            HttpResponse<byte[]> head = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(base + "/a.txt"))
                            .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, head.statusCode());
            assertEquals(String.valueOf(body.length), header(head, "content-length"),
                    "HEAD must report the length it would have sent");
            assertEquals(0, head.body().length, "HEAD must send no body");
            assertTrue(header(head, "content-type").startsWith("text/plain"));
            return null;
        });
    }

    @Test
    void headOnAMissingFileIs404(@TempDir Path docroot) throws Exception {
        withServer(docroot, base -> {
            HttpResponse<byte[]> head = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(base + "/nope.txt"))
                            .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(404, head.statusCode());
            return null;
        });
    }

    @Test
    void writeMethodsOnAnExistingFileAre405WithAllowHeader(@TempDir Path docroot) throws Exception {
        Files.writeString(docroot.resolve("a.txt"), "x");
        withServer(docroot, base -> {
            for (String method : List.of("POST", "PUT", "DELETE")) {
                HttpResponse<byte[]> r = HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder(URI.create(base + "/a.txt"))
                                .method(method, HttpRequest.BodyPublishers.ofString("")).build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(405, r.statusCode(), () -> method + " on a served file must be 405");
                assertEquals("GET, HEAD", header(r, "allow"), () -> method + " must advertise Allow");
            }
            return null;
        });
    }

    /** The AU channel is a POST and must keep working; 405 must not swallow it. */
    @Test
    void postToZkauIsStillTheAuStub(@TempDir Path docroot) throws Exception {
        withServer(docroot, base -> {
            HttpResponse<byte[]> r = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(base + "/zkau"))
                            .POST(HttpRequest.BodyPublishers.ofString("")).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, r.statusCode());
            assertEquals(StubEngine.AU_BODY, new String(r.body(), StandardCharsets.UTF_8));
            return null;
        });
    }

    // ---------------------------------------------------------------- R5, R6

    @Test
    void missingFileIs404WithEmptyBody(@TempDir Path docroot) throws Exception {
        withServer(docroot, base -> {
            HttpResponse<byte[]> r = getBytes(base + "/assets/missing.png");
            assertEquals(404, r.statusCode());
            assertEquals(0, r.body().length, "a 404 must carry no body");
            return null;
        });
    }

    @Test
    void directoryIs404AndItsContentsAreNeverListed(@TempDir Path docroot) throws Exception {
        Files.createDirectories(docroot.resolve("assets"));
        Files.writeString(docroot.resolve("assets/secret-name.png"), "x");
        Files.writeString(docroot.resolve("index.html"), "would be an index fallback");

        withServer(docroot, base -> {
            for (String p : List.of("/assets", "/assets/", "/")) {
                HttpResponse<byte[]> r = getBytes(base + p);
                assertEquals(404, r.statusCode(), () -> p + " must not be served");
                String body = new String(r.body(), StandardCharsets.UTF_8);
                assertFalse(body.contains("secret-name"),
                        () -> "a directory listing would disclose the working tree: " + body);
                assertFalse(body.contains("would be an index fallback"),
                        () -> p + " must not fall back to index.html: " + body);
            }
            return null;
        });
    }

    // ---------------------------------------------------------------- R7: never cache

    @Test
    void staticResponsesAreNeverStoredAndNeverConditional(@TempDir Path docroot) throws Exception {
        Files.writeString(docroot.resolve("a.css"), ".a{color:red}");
        withServer(docroot, base -> {
            for (int i = 0; i < 2; i++) {
                HttpResponse<byte[]> r = getBytes(base + "/a.css");
                assertEquals(200, r.statusCode(), "every request must be answered afresh, never 304");
                assertEquals("no-store, no-cache, must-revalidate", header(r, "cache-control"),
                        "an edited asset would otherwise repaint from the browser cache");
                assertEquals("no-cache", header(r, "pragma"));
                assertNull(header(r, "etag"), "an ETag invites a 304, which would show a stale asset");
                assertNull(header(r, "last-modified"), "Last-Modified invites a 304");
            }
            return null;
        });
    }

    // ---------------------------------------------------------------- R8, R9

    @Test
    void aLargeFileIsServedIntact(@TempDir Path docroot) throws Exception {
        int size = 12 * 1024 * 1024;
        byte[] big = new byte[size];
        for (int i = 0; i < size; i++) {
            big[i] = (byte) (i * 31);
        }
        Files.write(docroot.resolve("big.bin"), big);

        withServer(docroot, base -> {
            HttpResponse<byte[]> r = getBytes(base + "/big.bin");
            assertEquals(200, r.statusCode());
            assertEquals(String.valueOf(size), header(r, "content-length"));
            assertArrayEquals(big, r.body(), "a large asset must round-trip byte-identically");
            return null;
        });
    }

    /**
     * Asset requests arrive in parallel with, and during, the page render that triggered them.
     * If the file route sat behind the render dispatch, a slow page would stall its own assets.
     */
    @Test
    void assetsAreServedWhileARenderIsStillBlocked(@TempDir Path docroot) throws Exception {
        Files.writeString(docroot.resolve("a.css"), ".a{}");
        StubEngine engine = new StubEngine();
        PreviewHttpServer server = new PreviewHttpServer(engine, 0, null, docroot);
        server.start();
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            String base = "http://127.0.0.1:" + server.getPort();
            pool.submit(() -> getBytes(base + "/block.zul"));
            assertTrue(engine.blockingRenderStarted.await(10, TimeUnit.SECONDS),
                    "the blocking render never started");

            List<Future<HttpResponse<byte[]>>> assets = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                assets.add(pool.submit(() -> getBytes(base + "/a.css")));
            }
            for (Future<HttpResponse<byte[]>> f : assets) {
                assertEquals(200, f.get(15, TimeUnit.SECONDS).statusCode(),
                        "an asset must not wait on an unrelated render");
            }
        } finally {
            engine.release.countDown();
            pool.shutdownNow();
            server.stop();
        }
    }

    // ---------------------------------------------------------------- S1, S2: confinement

    /** {@code HttpClient} collapses {@code ..} client-side, so these must go over a raw socket. */
    @ParameterizedTest
    @ValueSource(strings = {
            "/../outside.txt",
            "/../../outside.txt",
            "/assets/../../outside.txt",
            "/./../outside.txt",
            "/%2e%2e/outside.txt",
            "/%2E%2E/outside.txt",
            "/assets/%2e%2e/%2e%2e/outside.txt",
            "/%2e%2e%2foutside.txt",
    })
    void pathTraversalIsRejected(String target, @TempDir Path parent) throws Exception {
        Path docroot = Files.createDirectories(parent.resolve("docroot"));
        Files.createDirectories(docroot.resolve("assets"));
        Files.writeString(parent.resolve("outside.txt"), "SECRET OUTSIDE THE DOCROOT");

        withServer(docroot, base -> {
            RawResponse r = raw(base, "GET", target);
            assertEquals(404, r.status, () -> target + " must not escape the docroot: " + r.head);
            assertFalse(r.body.contains("SECRET OUTSIDE THE DOCROOT"),
                    () -> target + " leaked a file outside the docroot");
            return null;
        });
    }

    /** S1 compares path components, so a sibling directory sharing the docroot's name prefix is out. */
    @Test
    void siblingDirectoryWithASharedNamePrefixIsRejected(@TempDir Path parent) throws Exception {
        Path docroot = Files.createDirectories(parent.resolve("app"));
        Files.createDirectories(parent.resolve("app-secrets"));
        Files.writeString(parent.resolve("app-secrets/keys.txt"), "SIBLING SECRET");

        withServer(docroot, base -> {
            RawResponse r = raw(base, "GET", "/../app-secrets/keys.txt");
            assertEquals(404, r.status, () -> "a name-prefix sibling must not be reachable: " + r.head);
            assertFalse(r.body.contains("SIBLING SECRET"));
            return null;
        });
    }

    @Test
    void nulByteInThePathIsRejected(@TempDir Path docroot) throws Exception {
        Files.writeString(docroot.resolve("a.txt"), "x");
        withServer(docroot, base -> {
            RawResponse r = raw(base, "GET", "/a.txt%00.png");
            assertNotEquals(200, r.status, () -> "a NUL byte must be refused, not truncated: " + r.head);
            return null;
        });
    }

    /**
     * A backslash must never act as a path separator (it is one on Windows). Two layers apply and
     * both are asserted, because only the second is ours: the JDK's own request parser rejects a
     * raw backslash in the request line with 400 before any handler runs, while a
     * percent-encoded one ({@code %5C}) reaches the route and is refused by its own check -- which
     * is what keeps that check from being dead code.
     */
    @Test
    void backslashIsNotAcceptedAsASeparator(@TempDir Path parent) throws Exception {
        Path docroot = Files.createDirectories(parent.resolve("docroot"));
        Files.writeString(parent.resolve("outside.txt"), "SECRET OUTSIDE THE DOCROOT");
        withServer(docroot, base -> {
            RawResponse raw = raw(base, "GET", "/..\\outside.txt");
            assertEquals(400, raw.status,
                    () -> "a raw backslash is refused by the JDK request parser: " + raw.head);
            assertFalse(raw.body.contains("SECRET OUTSIDE THE DOCROOT"));

            RawResponse encoded = raw(base, "GET", "/..%5Coutside.txt");
            assertEquals(404, encoded.status,
                    () -> "an encoded backslash must be refused by the route: " + encoded.head);
            assertFalse(encoded.body.contains("SECRET OUTSIDE THE DOCROOT"));
            return null;
        });
    }

    @Test
    void malformedPercentEncodingIsRejectedNotRepaired(@TempDir Path docroot) throws Exception {
        Files.writeString(docroot.resolve("a.txt"), "x");
        withServer(docroot, base -> {
            for (String target : List.of("/a%zz.txt", "/a%2.txt", "/a%.txt")) {
                RawResponse r = raw(base, "GET", target);
                assertNotEquals(200, r.status,
                        () -> target + " must be refused rather than repaired: " + r.head);
            }
            return null;
        });
    }

    // ---------------------------------------------------------------- S4, S5

    @ParameterizedTest
    @ValueSource(strings = {
            "/WEB-INF/web.xml",
            "/web-inf/web.xml",
            "/Web-Inf/web.xml",
            "/META-INF/MANIFEST.MF",
            "/meta-inf/MANIFEST.MF",
            "/assets/WEB-INF/nested.txt",
            "/assets/deeper/META-INF/nested.txt",
    })
    void webInfAndMetaInfAreNeverServedAtAnyDepthOrCase(String target, @TempDir Path docroot)
            throws Exception {
        Path file = docroot.resolve(target.substring(1));
        Files.createDirectories(file.getParent());
        Files.writeString(file, "CONFIDENTIAL DEPLOYMENT DESCRIPTOR");

        withServer(docroot, base -> {
            RawResponse r = raw(base, "GET", target);
            assertEquals(404, r.status, () -> target + " must never be served: " + r.head);
            assertFalse(r.body.contains("CONFIDENTIAL DEPLOYMENT DESCRIPTOR"),
                    () -> target + " leaked");
            return null;
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"/.env", "/.git/config", "/assets/.hidden/secret.txt", "/assets/.npmrc"})
    void dotfilesAreNeverServed(String target, @TempDir Path docroot) throws Exception {
        Path file = docroot.resolve(target.substring(1));
        Files.createDirectories(file.getParent());
        Files.writeString(file, "DOTFILE SECRET");

        withServer(docroot, base -> {
            RawResponse r = raw(base, "GET", target);
            assertEquals(404, r.status, () -> target + " must never be served: " + r.head);
            assertFalse(r.body.contains("DOTFILE SECRET"), () -> target + " leaked");
            return null;
        });
    }

    // ---------------------------------------------------------------- S6 and the null-docroot case

    @Test
    void listeningSocketIsBoundToLoopbackNotWildcard() throws Exception {
        PreviewHttpServer server = new PreviewHttpServer(new StubEngine(), 0);
        try {
            Field f = PreviewHttpServer.class.getDeclaredField("httpServer");
            f.setAccessible(true);
            com.sun.net.httpserver.HttpServer inner = (com.sun.net.httpserver.HttpServer) f.get(server);
            InetSocketAddress addr = inner.getAddress();
            assertTrue(addr.getAddress().isLoopbackAddress(),
                    () -> "serving files off a wildcard bind would expose the working tree to the "
                            + "local network; bound to " + addr);
            assertFalse(addr.getAddress().isAnyLocalAddress(), () -> "bound to " + addr);
        } finally {
            server.stop();
        }
    }

    /** Constructed without a docroot (the two-arg constructor), nothing may be read from disk. */
    @Test
    void withoutADocrootNothingIsServedFromDisk(@TempDir Path docroot) throws Exception {
        Files.writeString(docroot.resolve("a.txt"), "x");
        PreviewHttpServer server = new PreviewHttpServer(new StubEngine(), 0);
        server.start();
        try {
            assertEquals(404, getBytes("http://127.0.0.1:" + server.getPort() + "/a.txt").statusCode());
        } finally {
            server.stop();
        }
    }

    // ---------------------------------------------------------------- harness

    private interface ServerBody<T> {
        T run(String base) throws Exception;
    }

    private static <T> T withServer(Path docroot, ServerBody<T> body) throws Exception {
        StubEngine engine = new StubEngine();
        PreviewHttpServer server = new PreviewHttpServer(engine, 0, null, docroot);
        server.start();
        try {
            return body.run("http://127.0.0.1:" + server.getPort());
        } finally {
            engine.release.countDown();
            server.stop();
        }
    }

    private static HttpResponse<byte[]> getBytes(String url) throws Exception {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build().send(
                HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(60)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String header(HttpResponse<?> r, String name) {
        return r.headers().firstValue(name).orElse(null);
    }

    private static final class RawResponse {
        final int status;
        final String head;
        final String body;

        RawResponse(int status, String head, String body) {
            this.status = status;
            this.head = head;
            this.body = body;
        }
    }

    /**
     * Sends the request target verbatim, bypassing {@code HttpClient}'s client-side normalisation
     * (which would collapse {@code ..} before the server ever saw it) -- the socket equivalent of
     * {@code curl --path-as-is}.
     */
    private static RawResponse raw(String base, String method, String target) throws IOException {
        URI uri = URI.create(base);
        try (Socket s = new Socket(uri.getHost(), uri.getPort())) {
            s.setSoTimeout(30_000);
            OutputStream out = s.getOutputStream();
            out.write((method + " " + target + " HTTP/1.1\r\n"
                    + "Host: " + uri.getHost() + ":" + uri.getPort() + "\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            out.flush();

            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            try (InputStream in = s.getInputStream()) {
                int n;
                while ((n = in.read(chunk)) != -1) {
                    buf.write(chunk, 0, n);
                }
            }
            String all = buf.toString(StandardCharsets.ISO_8859_1);
            int sep = all.indexOf("\r\n\r\n");
            String head = sep < 0 ? all : all.substring(0, sep);
            String body = sep < 0 ? "" : all.substring(sep + 4);
            int status = -1;
            if (head.startsWith("HTTP/")) {
                String[] parts = head.split("\\s+", 3);
                if (parts.length >= 2) {
                    try {
                        status = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException ignored) {
                        // leave -1; the assertion message carries the head
                    }
                }
            }
            return new RawResponse(status, head, body);
        }
    }

    /**
     * Stands in for a real ZK engine: no ZK jars needed, so these tests run offline and fast.
     * Marks its output distinctly so a route that wrongly returned file source is obvious, and can
     * block one render on demand for the concurrency case.
     */
    private static final class StubEngine implements RenderEngine {
        static final String RENDERED_MARKER = "STUB-RENDERED-PAGE";
        static final String RESOURCE_BODY = "/* from the classpath resource handler */";
        static final String AU_BODY = "{\"rid\":0,\"rs\":[]}";

        final CountDownLatch blockingRenderStarted = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override
        public RenderResult renderZul(String zulPath) {
            if (zulPath.contains("block")) {
                blockingRenderStarted.countDown();
                try {
                    release.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return RenderResult.success(
                    "<html><head></head><body>" + RENDERED_MARKER + "</body></html>");
        }

        @Override
        public RenderResult renderZul(String zulPath, Map<String, String> headers) {
            return renderZul(zulPath);
        }

        @Override
        public ResourceResult resource(String pathInfo) {
            if (pathInfo.endsWith("probe.css")) {
                return ResourceResult.of(200, "text/css",
                        RESOURCE_BODY.getBytes(StandardCharsets.UTF_8));
            }
            return ResourceResult.notFound();
        }

        @Override
        public byte[] auStub() {
            return AU_BODY.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void close() {
        }
    }
}
