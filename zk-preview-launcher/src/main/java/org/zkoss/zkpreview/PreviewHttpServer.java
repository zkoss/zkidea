package org.zkoss.zkpreview;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * JDK built-in HTTP server bridging plain HTTP to the mock servlet environment.
 * Dispatch:
 * <ul>
 *   <li>{@code GET *.zul} -> page render</li>
 *   <li>{@code GET /zkau/web/*} -> resource (extendlet-processed JS/CSS)</li>
 *   <li>{@code POST /zkau} -> benign AU stub (RESEARCH.md U1 Q3)</li>
 * </ul>
 */
public final class PreviewHttpServer {

    private final HttpServer httpServer;
    private final RenderEngine engine;

    public PreviewHttpServer(RenderEngine engine, int port) throws IOException {
        this.engine = engine;
        this.httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        this.httpServer.createContext("/", this::handle);
    }

    public void start() {
        httpServer.start();
    }

    public void stop() {
        httpServer.stop(0);
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
                RenderResult r = engine.renderZul(path);
                if (r.isSuccess()) {
                    send(exchange, 200, "text/html;charset=UTF-8", r.getHtml().getBytes(StandardCharsets.UTF_8));
                } else {
                    // Stage 2 hook (out of v1 scope, tasks/zul-preview/stage2-hook.md):
                    // this is the single wire-level point where a structured failure
                    // becomes the JSON a future consumer would observe.
                    send(exchange, 500, "application/json;charset=UTF-8",
                            r.toJson().getBytes(StandardCharsets.UTF_8));
                }
                return;
            }
            send(exchange, 404, "text/plain;charset=UTF-8", new byte[0]);
        } finally {
            exchange.close();
        }
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
