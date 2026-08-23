package org.zkoss.zkpreview;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JDK built-in HTTP server bridging plain HTTP to the mock servlet environment.
 * Dispatch:
 * <ul>
 *   <li>{@code GET *.zul} -> page render</li>
 *   <li>{@code GET /zkau/web/*} -> resource (extendlet-processed JS/CSS)</li>
 *   <li>{@code POST /zkau} -> benign AU stub (first paint never issues an AU round-trip)</li>
 * </ul>
 */
public final class PreviewHttpServer {

    // A hung render (pathological zscript loop, include/apply cycle) must not freeze every preview
    // tab sharing this JVM (M5). A small fixed daemon pool lets independent requests -- a second
    // tab's render, or a page's burst of /zkau/web resource fetches -- proceed while one is stuck,
    // instead of com.sun.net.httpserver's default serial dispatch on a single thread. Bounded so a
    // runaway can't spawn unlimited threads; safe for concurrency because each render now uses its
    // own session/request/response (see the engines' newSession(), review item L1).
    private static final int HANDLER_THREADS = 8;

    private final HttpServer httpServer;
    private final ExecutorService executor;
    private final RenderEngine engine;
    private final String reportEnv;
    private final Path webappDir;

    public PreviewHttpServer(RenderEngine engine, int port) throws IOException {
        this(engine, port, null, null);
    }

    public PreviewHttpServer(RenderEngine engine, int port, String reportEnv, Path webappDir) throws IOException {
        this.engine = engine;
        this.reportEnv = reportEnv;
        this.webappDir = webappDir;
        this.httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        this.httpServer.createContext("/", this::handle);
        this.executor = Executors.newFixedThreadPool(HANDLER_THREADS, daemonThreadFactory());
        this.httpServer.setExecutor(executor);
    }

    public void start() {
        httpServer.start();
    }

    public void stop() {
        httpServer.stop(0);
        executor.shutdownNow();
    }

    private static ThreadFactory daemonThreadFactory() {
        AtomicInteger seq = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "zk-preview-http-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    public int getPort() {
        return httpServer.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if ("POST".equalsIgnoreCase(method) && "/zkau".equals(path)) {
                byte[] body = engine.auStub();
                send(exchange, 200, "text/plain;charset=UTF-8", body);
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.startsWith("/zkau/")) {
                String pathInfo = path.substring("/zkau".length());
                ResourceResult r = engine.resource(pathInfo);
                if (!r.isFound()) {
                    send(exchange, 404, "text/plain;charset=UTF-8", new byte[0]);
                    return;
                }
                String contentType = r.getContentType() == null ? "application/octet-stream" : r.getContentType();
                send(exchange, r.getStatus(), contentType, r.getBody());
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith(".zul")) {
                RenderResult r = engine.renderZul(path, requestHeaders(exchange));
                noStore(exchange);
                reportControllers(exchange, r);
                if (r.isSuccess()) {
                    send(exchange, 200, "text/html;charset=UTF-8",
                            withCanvasBackground(r.getHtml()).getBytes(StandardCharsets.UTF_8));
                } else {
                    // Serve a formatted HTML error page so the browser shows a readable error,
                    // not the raw 500 JSON it used to paint verbatim (a broken .zul is the most
                    // frequent touchpoint of this feature -- files are broken half the time while
                    // editing). Changing the wire format is safe because no test asserts the
                    // HTTP-level body is JSON: the structured RenderError (r.getError()/toJson())
                    // is unchanged and remains the contract, and the place a future programmatic
                    // sink would tap (a server-side Consumer<RenderError> passed in here).
                    send(exchange, 500, "text/html;charset=UTF-8",
                            ErrorPageRenderer.render(r.getError(), reportEnv, readZulSource(path))
                                    .getBytes(StandardCharsets.UTF_8));
                }
                return;
            }
            send(exchange, 404, "text/plain;charset=UTF-8", new byte[0]);
        } finally {
            exchange.close();
        }
    }

    /**
     * The incoming request's headers, shaped for the mock request (P2-8), so ZK's server-side reads
     * -- {@code Executions.getCurrent().getHeader(...)}, {@code getBrowser()}, device resolution --
     * see the real browser instead of an empty map.
     *
     * <p>{@code com.sun.net.httpserver.Headers} is a multimap while the mock holds one value per
     * name, so the <em>first</em> value wins. That is not a compromise: it is exactly what
     * {@code HttpServletRequest.getHeader} promises for a repeated header ("the first head" of the
     * values). The knowing limitation is {@code getHeaders(name)}, which then reports that single
     * value rather than every one the browser sent -- no rendering input ZK reads needs more.
     *
     * <p>Deliberately scoped to the {@code .zul} render dispatch and NOT to the {@code /zkau/web/*}
     * resource branch above. Measured: with the browser's real {@code Accept-Encoding}
     * ("gzip, deflate, br, zstd") in the mock request, ZK's extendlets honour it and hand back gzip
     * bytes, but {@code ResourceResult} carries only status/contentType/body, so the
     * {@code Content-Encoding} is dropped and gzip is served labelled {@code text/javascript}. The
     * client then reports "Invalid or unexpected token" and "zk is not defined" and paints nothing.
     * If forwarding there is ever wanted, try the cheap fix first: the AU servlet is initialised
     * with no {@code compress} init param (see {@code AbstractRenderEngine}'s bootstrap, where the
     * layout servlet already gets {@code compress=false}), and {@code DHtmlUpdateServlet.init}
     * answers a false one with {@code ClassWebResource.setCompress(null)} -- no gzip at all, so
     * nothing would need to carry an encoding. Only failing that does it need
     * {@code Content-Encoding} carried through {@code ResourceResult}, or {@code Accept-Encoding}
     * stripped. Until one of them is done, do not "complete" this change.
     */
    private static Map<String, String> requestHeaders(HttpExchange exchange) {
        Map<String, String> headers = new HashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (values != null && !values.isEmpty()) {
                headers.put(name, values.get(0));
            }
        });
        return headers;
    }

    /**
     * States which controller mode produced this render, on every {@code .zul} response -- 200 and
     * 500 alike (P0-2 item 4: the mode must be reported in success <em>and</em> error output).
     *
     * <p>A response header rather than stdout for two reasons: the launcher's stdout is the
     * single-consumer {@code PREVIEW_PORT=} handshake channel {@code preview-zul.py}'s pump reads,
     * and the mode is a property of one render -- a caller that renders several pages from one
     * process (the IntelliJ plugin) gets the right answer per request this way.
     */
    private static void reportControllers(HttpExchange exchange, RenderResult r) {
        ControllerOutcome outcome = r.getControllers() == null ? ControllerOutcome.SKIPPED : r.getControllers();
        exchange.getResponseHeaders().set("X-ZK-Preview-Controllers", outcome.token());
        String failure = headerSafe(r.getControllerFailure());
        if (failure != null) {
            exchange.getResponseHeaders().set("X-ZK-Preview-Controller-Failure", failure);
        }
    }

    /**
     * A controller's exception message is arbitrary text: a CR/LF would split the header (or be
     * rejected by {@code com.sun.net.httpserver}), and a non-ASCII byte is not portable in a
     * header value. Flatten, transliterate and cap; {@code null}/blank yields {@code null} so the
     * header is simply omitted.
     */
    static String headerSafe(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length() && sb.length() < HEADER_VALUE_LIMIT; i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n' || c == '\t') {
                sb.append(' ');
            } else if (c < 0x20 || c > 0x7e) {
                sb.append('?');
            } else {
                sb.append(c);
            }
        }
        String value = sb.toString().trim();
        return value.isEmpty() ? null : value;
    }

    /** Bound for the sanitized failure header; matches the engine's own one-line cap. */
    private static final int HEADER_VALUE_LIMIT = 300;

    /** The failing {@code .zul}'s own source, for the error report's source block; {@code null}
     * if unavailable (no webapp dir, unreadable, or outside the docroot). */
    private String readZulSource(String path) {
        if (webappDir == null) {
            return null;
        }
        try {
            String rel = path.startsWith("/") ? path.substring(1) : path;
            Path root = webappDir.normalize();
            Path f = root.resolve(rel).normalize();
            if (!f.startsWith(root) || !Files.isRegularFile(f)) {
                return null;
            }
            return Files.readString(f);
        } catch (Exception e) {
            return null;
        }
    }

    /** Matches the document {@code <head>} opening tag (with or without attributes); {@code \b}
     * keeps it from matching a {@code <header>} body element. */
    private static final Pattern HEAD_OPEN = Pattern.compile("<head\\b[^>]*>", Pattern.CASE_INSENSITIVE);

    /**
     * Gives the served page a white "canvas" default, matching how a normal browser paints an
     * unstyled page. ZK's layout output sets no {@code html}/{@code body} background (verified
     * against {@code zk.wcs}: the top-level {@code html}/{@code body} selectors carry none), so in
     * a real browser the page shows the white UA canvas -- but {@code JBCefBrowser} initializes its
     * base paint to the IDE theme color (dark under Darcula), which then shows through and makes the
     * preview look black. Injecting {@code html{background:#fff}} as the FIRST child of {@code <head>}
     * reproduces the browser's UA default: because it precedes every page/theme stylesheet (and any
     * inline style) in the cascade, an explicit background a real page sets still wins, so the
     * preview stays faithful. Fail-open: returns the HTML unchanged if it has no {@code <head>}.
     *
     * <p>Applied here at the serving boundary (not in the render engines) so {@link RenderEngine}
     * output stays byte-identical to what the real ZK servlet produces.
     */
    static String withCanvasBackground(String html) {
        Matcher m = HEAD_OPEN.matcher(html);
        if (!m.find()) {
            return html;
        }
        int at = m.end();
        return html.substring(0, at) + "<style>html{background:#fff}</style>" + html.substring(at);
    }

    /**
     * Marks the rendered {@code .zul} (success page or error page alike) as never-store.
     *
     * <p>The response used to carry no cache directives at all -- no {@code Cache-Control}, no
     * {@code ETag}, no {@code Last-Modified} -- which leaves a browser free to keep the first
     * render and answer later loads from disk with nothing to revalidate against. That is exactly
     * what JCEF's Chromium cache did: verified in a live IDE session, the entry under
     * {@code jcef_cache/Cache/Cache_Data} still held the pre-edit body while this server was
     * already serving the new one. Because the pane reloads only on save, the stale paint was
     * permanent -- no later event would have corrected it.
     *
     * <p>Scoped to the page on purpose: the {@code /zkau/web/*} assets are ZK's own static JS/CSS,
     * unchanged for the life of the process, and re-fetching them on every save would make each
     * refresh needlessly slow.
     */
    private static void noStore(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
        exchange.getResponseHeaders().set("Pragma", "no-cache");
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
