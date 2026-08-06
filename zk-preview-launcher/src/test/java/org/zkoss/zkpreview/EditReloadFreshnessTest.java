package org.zkoss.zkpreview;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.zkoss.zkpreview.testutil.Variants;
import org.zkoss.zkpreview.testutil.ZkClasspathResolver;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The preview must render whatever is on disk <em>right now</em>, every time (AC-5): the pane
 * reloads only on save, so a request that answers from a stale cache leaves the user staring at
 * the previous edit with no further event coming to correct it.
 *
 * <p>Reproduces the reported edit-after-recovery sequence, which is what makes the staleness
 * visible: broken ZUL (error) -> fixed ZUL (renders) -> edited again. ZK's own
 * {@code ResourceCache} never caches a page whose parse threw, so step 2 always re-parses and
 * looks fine; step 1's success is what populates the cache, and step 3 -- a normal, quick edit --
 * is the one served stale.
 */
class EditReloadFreshnessTest {

    private static final String BROKEN = "<zk>\n    <\n</zk>\n";
    private static final String FIXED = "<zk>\n    <button/>\n</zk>\n";
    private static final String EDITED = "<zk>\n    <button/>abc\n</zk>\n";

    static Stream<Variants.Named> variants() {
        return Variants.both();
    }

    @ParameterizedTest(name = "an edit right after a successful render is not served stale [{0}]")
    @MethodSource("variants")
    void editAfterSuccessfulRenderIsNotServedStale(Variants.Named variant, @TempDir Path docroot) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        Path zul = docroot.resolve("page.zul");
        // Distinct, strictly increasing mtimes so the outcome can't hinge on filesystem
        // timestamp granularity -- a real save seconds later has a later mtime too.
        long baseMtime = System.currentTimeMillis() - 60_000;

        RenderEngine engine = RenderEngineFactory.create(res.jars, docroot, null);
        PreviewHttpServer server = new PreviewHttpServer(engine, 0);
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getPort() + "/page.zul";

            // 1. broken ZUL -> the preview shows an error page.
            write(zul, BROKEN, baseMtime);
            HttpResponse<String> broken = get(url);
            assertEquals(500, broken.statusCode(),
                    () -> "a malformed ZUL must render as an error: " + broken.body());

            // 2. fixed -> the button renders.
            write(zul, FIXED, baseMtime + 1_000);
            HttpResponse<String> fixed = get(url);
            assertEquals(200, fixed.statusCode(), () -> "the fixed ZUL must render: " + fixed.body());
            assertTrue(fixed.body().contains("zul.wgt.Button"),
                    () -> "the fixed ZUL must render the button: " + fixed.body());
            long renderedAt = System.currentTimeMillis();

            // 3. edited again -> the edit must show up.
            write(zul, EDITED, baseMtime + 2_000);
            long gap = System.currentTimeMillis() - renderedAt;
            HttpResponse<String> edited = get(url);

            // ZK's default resource-cache check period is 5s: an edit saved later than that
            // would be re-read even by the unfixed launcher, so such a run proves nothing.
            Assumptions.assumeTrue(gap < 5_000,
                    "inconclusive: the edit landed " + gap + "ms after the previous render, "
                            + "outside the window this test exercises");

            assertEquals(200, edited.statusCode(), () -> "the edited ZUL must render: " + edited.body());
            assertTrue(edited.body().contains("abc"),
                    () -> "the preview must reflect the file on disk, but the edit made " + gap
                            + "ms after the previous render was served from a stale cache: " + edited.body());
        } finally {
            server.stop();
            engine.close();
        }
    }

    private static void write(Path file, String content, long mtimeMillis) throws Exception {
        Files.writeString(file, content, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(file, FileTime.fromMillis(mtimeMillis));
    }

    private static HttpResponse<String> get(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
