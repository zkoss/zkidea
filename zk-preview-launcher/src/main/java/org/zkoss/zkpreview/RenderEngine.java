package org.zkoss.zkpreview;

import java.io.Closeable;
import java.util.Map;

/**
 * Servlet-API-specific bridge between the HTTP server and a target ZK version.
 * One instance owns one {@link ScopedZkClassLoader} (one target ZK classpath).
 */
public interface RenderEngine extends Closeable {

    /** @param zulPath servlet path of the ZUL, e.g. {@code "/plain.zul"} */
    RenderResult renderZul(String zulPath);

    /**
     * Same render, plus the headers of the HTTP request that asked for it (P2-8), copied into the
     * mock request so ZK's server-side reads -- {@code Executions.getCurrent().getHeader(...)},
     * {@code getBrowser()}, device resolution -- see a genuine browser request instead of an empty
     * header map.
     *
     * <p>The map is a <em>parameter</em> and deliberately not a {@code ThreadLocal}: with
     * {@code --run-controllers} the render is submitted to a one-shot executor thread created
     * inside {@code renderZul} itself, so a thread-local set by the HTTP handler would not be
     * visible to the thread that actually builds the request. It is the same reason
     * {@code IsolationScope} is set <em>inside</em> {@code renderOnce} rather than around it.
     *
     * <p>{@code default} rather than abstract, and it drops the headers: a stub engine that does
     * not care about them (the plugin's own callers, the concurrency test's latch engine, every
     * one-argument call site) keeps exactly today's behaviour without implementing anything.
     *
     * @param zulPath servlet path of the ZUL, e.g. {@code "/plain.zul"}
     * @param headers one value per header name; never {@code null}, may be empty
     */
    default RenderResult renderZul(String zulPath, Map<String, String> headers) {
        return renderZul(zulPath);
    }

    /** @param pathInfo the part of the URL after the {@code /zkau} prefix, e.g. {@code "/web/foo/js/zk/zk.wpd"} */
    ResourceResult resource(String pathInfo);

    /** Benign stub for the AU (async-update) POST channel. First paint never needs it: the initial
     *  response embeds the whole widget tree in its {@code zkmx([...])} bootstrap, so the client
     *  builds the DOM from that plus the {@code /zkau/web/*} resources alone -- the AU channel only
     *  carries post-load interactions. (Note {@code /zkau/web/*} GET resources are NOT optional:
     *  without them nothing paints at all.) */
    byte[] auStub();
}
