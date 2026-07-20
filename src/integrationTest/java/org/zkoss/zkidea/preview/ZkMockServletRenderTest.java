package org.zkoss.zkidea.preview;

import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@code DHtmlLayoutServlet} can render a ZUL file using mock
 * servlet objects — no Jetty, no child JVM.
 *
 * <p><b>Hypothesis</b>: Since preview only needs the initial HTML, we can
 * bypass the Jetty container entirely and call the servlet directly in-process.
 *
 * <p><b>Preconditions</b>:
 * <ul>
 *   <li>ZK JARs resolvable from {@code /Users/hawk/Documents/workspace/SUPPORT/plugin-test/pom.xml}</li>
 * </ul>
 *
 * <p><b>Pass</b> → hypothesis verified; production refactoring can proceed.<br>
 * <b>Fail</b> → assertion message identifies the broken layer exactly.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZkMockServletRenderTest {

    private static final String PLUGIN_TEST_POM =
            "/Users/hawk/Documents/workspace/SUPPORT/plugin-test/pom.xml";

    /** Inline ZUL — no dependency on external plugin-test webapp files. */
    private static final String TEST_ZUL =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<zk xmlns=\"http://www.zkoss.org/2005/zul\">\n" +
            "  <button label=\"Click me\"/>\n" +
            "</zk>";

    private static String zkClasspath;
    private static Path webappDir;

    @BeforeAll
    static void setup() throws Exception {
        // 1. Resolve ZK classpath
        zkClasspath = resolveZkClasspath();
        assertFalse(zkClasspath.isBlank(),
                "ZK classpath empty — check plugin-test pom.xml at " + PLUGIN_TEST_POM);

        // 2. Create temp webapp dir
        webappDir = Files.createTempDirectory("zk-mock-render-test-");
        Files.createDirectories(webappDir.resolve("WEB-INF"));

        writeMinimalZkConfig(webappDir.toFile());
        Files.writeString(webappDir.resolve("test.zul"), TEST_ZUL, StandardCharsets.UTF_8);
    }

    @Test
    @Order(1)
    void rendersZulViaDirectServletCallWithoutJetty() throws Exception {
        String html = ZkMockRenderer.render(zkClasspath, webappDir, "/test.zul");

        assertNotNull(html, "render() returned null");

        // ZK 10 renders an HTML page shell with JavaScript mount calls.
        // The <button> component appears as a JS descriptor, not an HTML <button> element.
        assertTrue(html.contains("<html"),
                "Expected <html> tag in output. Got: " + first500(html));
        assertTrue(html.contains("zul.wgt.Button"),
                "Expected 'zul.wgt.Button' JS mount call from ZUL. Got: " + first500(html));
        assertTrue(html.contains("Click me"),
                "Expected button label 'Click me' from ZUL. Got: " + first500(html));
        assertTrue(html.contains("zk."),
                "Expected ZK JS marker 'zk.' in output. Got: " + first500(html));
    }

    @AfterAll
    static void cleanup() {
        if (webappDir != null) {
            try {
                Files.walk(webappDir)
                     .sorted(Comparator.reverseOrder())
                     .map(Path::toFile)
                     .forEach(File::delete);
            } catch (IOException ignored) {}
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private static String resolveZkClasspath() throws IOException, InterruptedException {
        Path cpFile = Files.createTempFile("zk-mock-it-cp-", ".txt");
        try {
            ProcessBuilder mvn = new ProcessBuilder(
                    "mvn",
                    "-f", PLUGIN_TEST_POM,
                    "dependency:build-classpath",
                    "-Dmdep.outputFile=" + cpFile.toAbsolutePath(),
                    "-q"
            );
            mvn.redirectErrorStream(true);
            Process p = mvn.start();
            try (var r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                r.lines().forEach(l -> {}); // drain
            }
            boolean done = p.waitFor(120, TimeUnit.SECONDS);
            if (!done) { p.destroy(); throw new IOException("mvn timed out"); }
            if (p.exitValue() != 0) {
                throw new IOException("mvn dependency:build-classpath failed (exit " + p.exitValue() + ")");
            }
            return Files.readString(cpFile, StandardCharsets.UTF_8).trim();
        } finally {
            Files.deleteIfExists(cpFile);
        }
    }

    private static void writeMinimalZkConfig(File webappDir) throws IOException {
        File webInf = new File(webappDir, "WEB-INF");
        webInf.mkdirs();

        Files.writeString(new File(webInf, "zk.xml").toPath(),
                "<zk><system-config>" +
                "<disable-event-thread>true</disable-event-thread>" +
                "</system-config></zk>",
                StandardCharsets.UTF_8);
    }

    private static String first500(String s) {
        if (s == null) return "(null)";
        return s.substring(0, Math.min(500, s.length()));
    }
}
