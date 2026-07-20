package org.zkoss.zkpreview;

import java.io.Closeable;

/**
 * Servlet-API-specific bridge between the HTTP server and a target ZK version.
 * One instance owns one {@link ScopedZkClassLoader} (one target ZK classpath).
 */
public interface RenderEngine extends Closeable {

    /** @param zulPath servlet path of the ZUL, e.g. {@code "/plain.zul"} */
    RenderResult renderZul(String zulPath);

    /** @param pathInfo the part of the URL after the {@code /zkau} prefix, e.g. {@code "/web/foo/js/zk/zk.wpd"} */
    ResourceResult resource(String pathInfo);

    /** Benign stub for the AU (async-update) POST channel; first paint never needs it (RESEARCH.md U1 Q3). */
    byte[] auStub();
}
