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
 *   <li>{@code GET *.zul} -> page render; no such page -> the status ZK answered with
 *       ({@code 404}) and a plain-text line naming the path and the docroot</li>
 *   <li>{@code GET /zkau/web/*} -> resource (extendlet-processed JS/CSS)</li>
 *   <li>{@code POST /zkau} -> benign AU stub (first paint never issues an AU round-trip)</li>
 *   <li>{@code GET|HEAD <anything else>} -> a regular file from the {@code --webapp} docroot,
 *       confined to it; other methods on such a file get {@code 405}</li>
 *   <li>anything left -> empty {@code 404}</li>
 * </ul>
 *
 * <p>Order is precedence, and the docroot route is deliberately last: a {@code .zul} is always
 * rendered as a page rather than returned as source text, and {@code /zkau/**} always resolves off
 * the classpath rather than off disk. The docroot route is this server's stand-in for the
 * {@code DefaultServlet} a real servlet container would have contributed implicitly -- there being
 * no container here, nothing supplied it, which is why docroot assets went unserved until it was
 * added. See {@code doc/preview-launcher-architecture.md}.
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
                if (r.isNotServed()) {
                    // Before isSuccess(), and before anything reads getError(): a page that is not
                    // there has no error to render (#71).
                    send(exchange, r.getNotServedStatus(), "text/plain;charset=UTF-8",
                            notServedBody(r));
                } else if (r.isSuccess()) {
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
            // Last: the docroot's own files. Deliberately after the two ZK handlers above, so a
            // .zul is always rendered as a page and a /zkau/** path always resolves off the
            // classpath -- neither can be answered as file source by this branch.
            Path file = staticFile(exchange.getRequestURI().getRawPath());
            if (file != null) {
                if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
                    sendStaticFile(exchange, file);
                } else {
                    exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                    send(exchange, 405, "text/plain;charset=UTF-8", new byte[0]);
                }
                return;
            }
            send(exchange, 404, "text/plain;charset=UTF-8", new byte[0]);
        } finally {
            exchange.close();
        }
    }

    /**
     * Resolves a request path to a regular file inside the docroot, or {@code null} for anything
     * that must not be served -- which this server answers as a plain 404, disclosing nothing about
     * whether the path exists, is a directory, or was refused on principle.
     *
     * <p>Until this method existed, no request path was ever resolved against the filesystem, so
     * traversal was impossible by construction. That accidental safety is gone, and everything
     * below replaces it. The order matters: <b>decode first, then validate</b>, so {@code %2e%2e}
     * is rejected by the same check as {@code ../} rather than sneaking past a check that ran on
     * the still-encoded form.
     *
     * <p>Refused, each by an explicit rule rather than as a side effect of path arithmetic:
     * malformed percent-escapes (rejected, never repaired), a NUL byte, a backslash (a separator on
     * Windows and never legitimate in a URL path here), any {@code ..} component, any component
     * starting with {@code .} (keeps {@code .git/}, {@code .env} and editor state unreachable), and
     * any {@code WEB-INF} / {@code META-INF} component at any depth in any case (those hold
     * {@code web.xml}, {@code zk.xml}, and in a built webapp the application's own classes).
     *
     * <p>Containment is then re-checked on the resolved path via {@link Path#startsWith}, which
     * compares name elements rather than string prefixes -- so a docroot of {@code /home/u/app}
     * does not admit {@code /home/u/app-secrets/x}.
     *
     * <p><b>Documented limitation, by decision -- not an oversight.</b> {@code normalize()} is
     * lexical: it does not resolve symbolic links, so a symlink inside the docroot pointing outside
     * it <em>is</em> served. Refusing it is a withdrawn requirement (spec S3), for three reasons:
     * a docroot-bounded {@code toRealPath()} would 404 legitimate assets, because a preview docroot
     * is a live source tree where symlinked asset folders are normal; the threat model does not
     * justify it, since previewing an untrusted project already grants code execution in this JVM
     * through {@code <zscript>}, next to which reading one file through a link is a strict
     * downgrade; and the only non-breaking version -- bounding the check by the project's content
     * roots -- costs more than all the other confinement rules combined for no change in what an
     * attacker can do. This server is a developer tool on {@code 127.0.0.1} pointed at the
     * developer's own tree, and must not be used to serve untrusted content. If ever revisited,
     * bound the check by the content roots, never by the docroot.
     */
    private Path staticFile(String rawPath) {
        if (webappDir == null || rawPath == null) {
            return null;
        }
        String decoded = decodeStrict(rawPath);
        if (decoded == null || decoded.indexOf('\0') >= 0 || decoded.indexOf('\\') >= 0) {
            return null;
        }
        Path root = webappDir.toAbsolutePath().normalize();
        Path resolved = root;
        for (String part : decoded.split("/")) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if (part.startsWith(".") || "WEB-INF".equalsIgnoreCase(part) || "META-INF".equalsIgnoreCase(part)) {
                return null;
            }
            resolved = resolved.resolve(part);
        }
        resolved = resolved.normalize();
        if (!resolved.startsWith(root) || !Files.isRegularFile(resolved)) {
            return null;
        }
        return resolved;
    }

    /**
     * Percent-decodes a URL path, returning {@code null} rather than a repaired string when an
     * escape is malformed (a truncated or non-hex {@code %} sequence). Decodes the bytes first and
     * interprets the result as UTF-8, so a multi-byte character split across escapes survives.
     * {@code +} is left alone: it means a literal plus in a path, not a space.
     */
    private static String decodeStrict(String rawPath) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(rawPath.length());
        for (int i = 0; i < rawPath.length(); i++) {
            char c = rawPath.charAt(i);
            if (c != '%') {
                if (c > 0x7F) {
                    // A raw non-ASCII char in the request line: keep its UTF-8 bytes.
                    out.writeBytes(String.valueOf(c).getBytes(StandardCharsets.UTF_8));
                } else {
                    out.write(c);
                }
                continue;
            }
            if (i + 2 >= rawPath.length()) {
                return null;
            }
            int hi = Character.digit(rawPath.charAt(i + 1), 16);
            int lo = Character.digit(rawPath.charAt(i + 2), 16);
            if (hi < 0 || lo < 0) {
                return null;
            }
            out.write((hi << 4) | lo);
            i += 2;
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    /**
     * Streams a docroot file. Never read whole into memory ({@link Files#copy}): a webapp's assets
     * reach tens of megabytes and the launcher runs on the default heap.
     *
     * <p>Carries the same no-store headers as a rendered page, and deliberately no {@code ETag} or
     * {@code Last-Modified}. The pane re-requests the same URLs on every save while the developer
     * is editing those very files, so a {@code 304} would repaint a previous version -- the exact
     * failure the page handler's cache directives already exist to prevent.
     */
    private static void sendStaticFile(HttpExchange exchange, Path file) throws IOException {
        long size = Files.size(file);
        noStore(exchange);
        exchange.getResponseHeaders().set("Content-Type", contentType(file.getFileName().toString()));
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod()) || size == 0) {
            // -1 means "no body"; the length still has to be advertised by hand, because the
            // JDK server only derives Content-Length from a body it is actually going to write.
            exchange.getResponseHeaders().set("Content-Length", Long.toString(size));
            exchange.sendResponseHeaders(200, -1);
            return;
        }
        exchange.sendResponseHeaders(200, size);
        try (OutputStream os = exchange.getResponseBody()) {
            Files.copy(file, os);
        }
    }

    /**
     * The diagnostic a caller gets instead of an empty body (#71).
     *
     * <p>Two lines: the engine's reason, and the docroot the path was resolved against -- the fact
     * that turns "no such page" into an actionable answer, because a mistyped preview path and a
     * launcher pointed at the wrong docroot look identical from the outside. Plain text rather than
     * an HTML error page, and a {@code 404} rather than a {@code 500}, so that this stays
     * distinguishable from a render failure: {@code preview-zul.py} scrapes an error page only for
     * {@code status >= 500}, and reports anything else as "the render server answered HTTP
     * &lt;status&gt;" with the hint to check the path -- which is the right advice here.
     */
    private byte[] notServedBody(RenderResult r) {
        StringBuilder sb = new StringBuilder()
                .append("HTTP ").append(r.getNotServedStatus()).append(": ")
                .append(r.getNotServedReason()).append('\n');
        if (webappDir != null) {
            sb.append("docroot: ").append(webappDir.toAbsolutePath().normalize()).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Extension-to-MIME mapping for docroot files.
     *
     * <p>Owned here rather than delegated to {@code ServletContext.getMimeType}, which the mock
     * context always answers {@code null} -- an asset served with no {@code Content-Type} is
     * refused outright by a strict browser, which would look exactly like the bug this route fixes.
     */
    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("css", "text/css"),
            Map.entry("js", "text/javascript"),
            Map.entry("mjs", "text/javascript"),
            Map.entry("json", "application/json"),
            Map.entry("map", "application/json"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("webp", "image/webp"),
            Map.entry("ico", "image/vnd.microsoft.icon"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("ttf", "font/ttf"),
            Map.entry("eot", "application/vnd.ms-fontobject"),
            Map.entry("txt", "text/plain"),
            Map.entry("html", "text/html"),
            Map.entry("htm", "text/html"));

    /**
     * {@code Content-Type} for a file name. Unknown extensions get
     * {@code application/octet-stream}; {@code text/*} additionally declares UTF-8, matching the
     * charset every other response from this server uses.
     */
    static String contentType(String fileName) {
        String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        String type = dot < 0 ? null : CONTENT_TYPES.get(lower.substring(dot + 1));
        if (type == null) {
            return "application/octet-stream";
        }
        return type.startsWith("text/") ? type + ";charset=UTF-8" : type;
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
        // A HEAD must carry no body, and the JDK server logs a warning if one is announced for it.
        // Reachable here only via the docroot route's 404/405, the sole non-GET paths in this
        // server; every other caller is GET-only and behaves exactly as before.
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(body.length));
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
