package org.zkoss.zkpreview;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.zkoss.zkpreview.testutil.ZkClasspathResolver;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M5 (code review): the preview HTTP server must not dispatch requests serially on one thread --
 * one slow or hung render (a pathological {@code <zscript>} loop, an include/apply cycle) would
 * otherwise freeze <em>every</em> preview tab sharing the helper JVM, with no self-recovery. A
 * stub engine whose one path blocks lets us assert a second request is still served while the
 * first render is stuck. (Safe only because L1 gives each render its own session/request/response;
 * the stub sidesteps ZK entirely so the test is deterministic and needs no ZK jars.)
 */
class PreviewHttpServerConcurrencyTest {

    /** A render engine where {@code /block.zul} hangs until released; everything else is instant. */
    private static final class LatchEngine implements RenderEngine {
        final CountDownLatch blockingRenderStarted = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override
        public RenderResult renderZul(String zulPath) {
            if (zulPath.contains("block")) {
                blockingRenderStarted.countDown();
                try {
                    release.await(15, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return RenderResult.success("<html><head></head><body>blocked done</body></html>");
            }
            return RenderResult.success("<html><head></head><body>quick</body></html>");
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
    void aStuckRenderDoesNotBlockOtherRequests() throws Exception {
        LatchEngine engine = new LatchEngine();
        PreviewHttpServer server = new PreviewHttpServer(engine, 0);
        server.start();
        int port = server.getPort();

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        ExecutorService background = Executors.newSingleThreadExecutor();
        try {
            // Request A: a render that hangs inside the engine.
            Future<Integer> blocked = background.submit(() -> client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + port + "/block.zul")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode());

            assertTrue(engine.blockingRenderStarted.await(5, TimeUnit.SECONDS),
                    "the blocking render should have started");

            // Request B: must be served while A is still stuck. On a single-threaded server this
            // request never gets dispatched (the sole thread is inside A) and times out.
            HttpResponse<String> quick = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + port + "/quick.zul"))
                            .timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            assertEquals(200, quick.statusCode(),
                    "a second request must be served while the first render is still blocked (M5)");
            assertTrue(quick.body().contains("quick"), quick.body());

            // Release A and confirm it too completes.
            engine.release.countDown();
            assertEquals(200, blocked.get(15, TimeUnit.SECONDS), "the released render must complete");
        } finally {
            engine.release.countDown();
            background.shutdownNow();
            server.stop();
        }
    }

    /**
     * De-risks M5: several <em>real</em> ZK renders fired at once (released together via a barrier
     * to maximise server-side overlap) must all succeed. This exercises the fixed pool driving
     * concurrent renders through one shared engine -- safe only because L1 gave each render its own
     * session/request/response. Skips cleanly when ZK jars can't be resolved in this environment.
     */
    @Test
    void concurrentRealRendersAllSucceed() throws Exception {
        ZkClasspathResolver.Resolution res = ZkClasspathResolver.resolveJakarta();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        int n = 6;
        RenderEngine engine = RenderEngineFactory.create(res.jars, Paths.get("src/test/resources/fixtures"), null);
        PreviewHttpServer server = new PreviewHttpServer(engine, 0);
        server.start();
        int port = server.getPort();
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            CyclicBarrier startTogether = new CyclicBarrier(n);
            List<Future<HttpResponse<String>>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(() -> {
                    startTogether.await(10, TimeUnit.SECONDS);
                    return client.send(HttpRequest.newBuilder()
                                    .uri(URI.create("http://127.0.0.1:" + port + "/plain.zul"))
                                    .timeout(Duration.ofSeconds(30)).GET().build(),
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                }));
            }
            for (Future<HttpResponse<String>> f : futures) {
                HttpResponse<String> r = f.get(40, TimeUnit.SECONDS);
                assertEquals(200, r.statusCode(), () -> "concurrent render failed: " + r.body());
                assertTrue(r.body().contains("Hello ZK"), () -> "unexpected render body: " + r.body());
            }
        } finally {
            pool.shutdownNow();
            server.stop();
            engine.close();
        }
    }
}
