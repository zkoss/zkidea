package org.zkoss.zkpreview;

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

import static org.junit.jupiter.api.Assertions.*;

/**
 * The preview serves ZK's page HTML with a white "canvas" default injected at the top of
 * {@code <head>}. Rationale: ZK's layout output sets no html/body background, so a real
 * browser paints the white UA canvas -- but {@link com.intellij.ui.jcef.JBCefBrowser}
 * initializes its base paint to the IDE theme color (dark under Darcula), which shows
 * through and makes the preview look black. The injected rule reproduces the browser
 * default and stays overridable by any explicit background a real page/theme sets (those
 * come later in the cascade), so faithfulness is preserved.
 */
class PreviewHttpServerTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");

    @Test
    void withCanvasBackground_insertsWhiteCanvasImmediatelyAfterHead() {
        String out = PreviewHttpServer.withCanvasBackground(
                "<!DOCTYPE html><html><head><title>x</title></head><body>hi</body></html>");
        assertTrue(out.contains("html{background:#fff}"), out);
        // Immediately after the <head> opening tag, before anything else in the head.
        assertTrue(out.contains("<head><style>html{background:#fff}</style><title>"), out);
    }

    @Test
    void withCanvasBackground_precedesPageStylesSoTheyCanOverrideIt() {
        String out = PreviewHttpServer.withCanvasBackground(
                "<html><head><link rel=\"stylesheet\" href=\"/zkau/web/x.wcs\"/></head><body/></html>");
        int injected = out.indexOf("html{background:#fff}");
        int firstLink = out.indexOf("<link");
        assertTrue(injected >= 0 && firstLink >= 0, out);
        assertTrue(injected < firstLink,
                "injected canvas default must precede page stylesheets so they win: " + out);
    }

    @Test
    void withCanvasBackground_matchesHeadWithAttributesButNotHeader() {
        String withAttrs = PreviewHttpServer.withCanvasBackground(
                "<html><head lang=\"en\"><meta/></head><body><header>nav</header></body></html>");
        assertTrue(withAttrs.contains("<head lang=\"en\"><style>html{background:#fff}</style><meta/>"), withAttrs);
        // The <header> body element must be untouched.
        assertTrue(withAttrs.contains("<header>nav</header>"), withAttrs);
        assertEquals(1, countOccurrences(withAttrs, "html{background:#fff}"), withAttrs);
    }

    @Test
    void withCanvasBackground_noHead_returnsUnchanged() {
        String in = "<html><body>no head here</body></html>";
        assertEquals(in, PreviewHttpServer.withCanvasBackground(in));
    }

    /** End-to-end: the white canvas default is present in the page the server actually serves. */
    @Test
    void servedPageCarriesWhiteCanvasDefault() throws Exception {
        ZkClasspathResolver.Resolution res = ZkClasspathResolver.resolveJakarta();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, null);
        PreviewHttpServer server = new PreviewHttpServer(engine, 0);
        server.start();
        try {
            String html = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort() + "/plain.zul"))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
            assertTrue(html.contains("<head><style>html{background:#fff}</style>"),
                    "served page must carry the white canvas default right after <head>: " + html);
        } finally {
            server.stop();
            engine.close();
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int n = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        return n;
    }
}
