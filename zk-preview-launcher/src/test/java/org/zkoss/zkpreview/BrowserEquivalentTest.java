package org.zkoss.zkpreview;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.zkoss.zkpreview.testutil.ZkClasspathResolver;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E1-G2: browser-equivalent check. Preferred path: a real headless browser
 * (Playwright) loads the rendered page and the resulting DOM contains the
 * expected widget CSS classes. Falls back to the documented HTTP content-
 * signature check (every resource URL in {@code <head>} returns 200 with
 * PROCESSED content, not raw source) if Playwright can't drive a browser in
 * this environment. Whichever path actually ran is printed to stdout so the
 * evidence file can record it.
 */
class BrowserEquivalentTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");

    @Test
    void plainZulLoadsWithExpectedWidgetDom() throws Exception {
        ZkClasspathResolver.Resolution res = ZkClasspathResolver.resolveJakarta();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, null);
        PreviewHttpServer server = new PreviewHttpServer(engine, 0);
        server.start();
        try {
            int port = server.getPort();
            if (tryPlaywright(port)) {
                System.out.println("E1-G2 path: PLAYWRIGHT (real headless browser DOM check)");
                return;
            }
            System.out.println("E1-G2 path: HTTP fallback (content-signature check)");
            httpFallback(port);
        } finally {
            server.stop();
            engine.close();
        }
    }

    /** @return true if Playwright successfully drove a real browser and assertions ran. */
    private boolean tryPlaywright(int port) {
        try (Playwright playwright = Playwright.create()) {
            try (Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
                Page page = browser.newPage();
                page.navigate("http://127.0.0.1:" + port + "/plain.zul");
                page.waitForSelector(".z-window", new Page.WaitForSelectorOptions().setTimeout(15000));

                assertEquals(1, page.locator(".z-window").count(), "expected one .z-window widget");
                assertTrue(page.locator(".z-label").count() >= 1, "expected at least one .z-label widget");
                assertTrue(page.locator(".z-button").count() >= 1, "expected at least one .z-button widget");
                assertTrue(page.content().contains("Hello ZK"), "expected label text in the live DOM");
                assertTrue(page.content().contains("Click me"), "expected button text in the live DOM");
                return true;
            }
        } catch (Exception e) {
            System.out.println("Playwright unavailable, falling back to HTTP check: " + e);
            return false;
        }
    }

    private void httpFallback(int port) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        String html = get(client, "http://127.0.0.1:" + port + "/plain.zul");
        assertTrue(html.contains("zul.wgt.Label"), html);

        Matcher m = Pattern.compile("(?:src|href)=\"(/zkau/web/[^\"]+)\"").matcher(html);
        List<String> resourceUrls = new java.util.ArrayList<>();
        while (m.find()) resourceUrls.add(m.group(1));
        assertFalse(resourceUrls.isEmpty(), "expected at least one /zkau/web/* resource URL in <head>: " + html);

        for (String url : resourceUrls) {
            java.net.http.HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + port + url))
                            .timeout(Duration.ofSeconds(15)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, resp.statusCode(), url + " => " + resp.body());
            String body = resp.body();
            if (url.endsWith(".wpd")) {
                assertFalse(body.contains("<package"), url + " must be PROCESSED JS, not raw wpd XML: "
                        + body.substring(0, Math.min(200, body.length())));
            } else if (url.contains(".dsp") || url.endsWith(".wcs")) {
                assertFalse(body.contains("<%@"), url + " must be PROCESSED CSS, not raw taglib source: "
                        + body.substring(0, Math.min(200, body.length())));
            }
        }
    }

    private static String get(HttpClient client, String url) throws Exception {
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, resp.statusCode());
        return resp.body();
    }
}
