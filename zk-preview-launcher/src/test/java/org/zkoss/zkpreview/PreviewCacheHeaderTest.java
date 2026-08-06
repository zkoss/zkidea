package org.zkoss.zkpreview;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zkoss.zkpreview.testutil.ZkClasspathResolver;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A rendered {@code .zul} is a live resource -- it changes on every save -- so it must never be
 * stored by the browser's HTTP cache.
 *
 * <p>This is not theoretical. The response used to carry only {@code Content-Type}/
 * {@code Content-Length}: no {@code Cache-Control}, no {@code ETag}, no {@code Last-Modified}.
 * With nothing to revalidate against, JCEF's Chromium cache kept the first successful render and
 * answered the pane's reload from disk -- the pane sat on the previous edit while the server was
 * already serving the new one. (Confirmed against a live IDE session: the entry in
 * {@code jcef_cache/Cache/Cache_Data} held the pre-edit body.) The pane reloads only on save, so
 * nothing ever arrives to correct it.
 *
 * <p>The {@code /zkau/web/*} assets are deliberately left cacheable: they are ZK's own static
 * JS/CSS, they never change within a session, and re-fetching them on every save would make each
 * refresh needlessly slow.
 */
class PreviewCacheHeaderTest {

    @Test
    void renderedPageIsNeverStoredByTheBrowserCache(@TempDir Path docroot) throws Exception {
        ZkClasspathResolver.Resolution res = ZkClasspathResolver.resolveJakarta();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        Files.writeString(docroot.resolve("page.zul"), "<zk>\n    <button/>\n</zk>\n", StandardCharsets.UTF_8);

        RenderEngine engine = RenderEngineFactory.create(res.jars, docroot, null);
        PreviewHttpServer server = new PreviewHttpServer(engine, 0);
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getPort();

            HttpResponse<String> page = get(base + "/page.zul");
            assertEquals(200, page.statusCode(), () -> "the fixture must render: " + page.body());
            assertTrue(noStore(page),
                    () -> "a rendered .zul must be uncacheable, else the pane repaints a stale copy "
                            + "after a save; Cache-Control was: " + cacheControl(page));

            // The error page is the same live resource in its failing state.
            Files.writeString(docroot.resolve("broken.zul"), "<zk>\n    <\n</zk>\n", StandardCharsets.UTF_8);
            HttpResponse<String> error = get(base + "/broken.zul");
            assertEquals(500, error.statusCode(), () -> "the broken fixture must fail: " + error.body());
            assertTrue(noStore(error),
                    () -> "the error page must be uncacheable too; Cache-Control was: " + cacheControl(error));

            // ZK's own static assets stay cacheable -- refetching them on every save would slow
            // every refresh.
            HttpResponse<String> asset = get(base + "/zkau/web/js/zk.wpd");
            Assumptions.assumeTrue(asset.statusCode() == 200,
                    "skip: asset path not served in this ZK build (HTTP " + asset.statusCode() + ")");
            assertFalse(noStore(asset),
                    () -> "ZK's static assets must stay cacheable; Cache-Control was: " + cacheControl(asset));
        } finally {
            server.stop();
            engine.close();
        }
    }

    private static boolean noStore(HttpResponse<String> r) {
        return cacheControl(r).toLowerCase().contains("no-store");
    }

    private static String cacheControl(HttpResponse<String> r) {
        List<String> values = r.headers().allValues("Cache-Control");
        return values.isEmpty() ? "<absent>" : String.join(", ", values);
    }

    private static HttpResponse<String> get(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
