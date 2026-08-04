package org.zkoss.zkidea.preview;

import org.junit.jupiter.api.*;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that starts a real ZK preview server child JVM, serves a ZUL file,
 * makes an HTTP request, and asserts ZK actually rendered HTML.
 *
 * <p>Preconditions (ensured by Gradle {@code dependsOn}):
 * <ul>
 *   <li>{@code zk-preview-launcher/build/libs/zk-preview-launcher.jar} must exist</li>
 *   <li>plugin-test Maven project at {@code /Users/hawk/Documents/workspace/SUPPORT/plugin-test}
 *       must be resolvable (has ZK 10 in its pom.xml)</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZkPreviewServerIntegrationTest {

    private static final String PLUGIN_TEST_POM =
            "/Users/hawk/Documents/workspace/SUPPORT/plugin-test/pom.xml";
    private static final String TEST_ZUL_SOURCE =
            "/Users/hawk/Documents/workspace/SUPPORT/plugin-test/src/main/webapp/test.zul";
    private static final String LAUNCHER_JAR =
            "zk-preview-launcher/build/libs/zk-preview-launcher.jar";

    private static Process serverProcess;
    private static int serverPort = -1;
    private static Path tempDir;
    private static Path logFile;

    @BeforeAll
    static void startServer() throws Exception {
        // 1. Resolve ZK classpath via Maven
        String zkClasspath = resolveZkClasspath();
        assertFalse(zkClasspath.isBlank(),
                "ZK classpath must not be empty — check plugin-test pom.xml");

        // 2. Locate the launcher JAR (guaranteed by Gradle dependsOn)
        File launcherJar = new File(LAUNCHER_JAR);
        assertTrue(launcherJar.exists(),
                "Launcher JAR not found at " + launcherJar.getAbsolutePath() +
                ". Run: ./gradlew :zk-preview-launcher:shadowJar");

        // 3. Set up temp webapp directory
        tempDir = Files.createTempDirectory("zk-preview-test-");
        logFile = tempDir.resolve("preview-server.log");
        Path webappDir = tempDir.resolve("webapp");
        Files.createDirectories(webappDir.resolve("WEB-INF"));

        writeMinimalZkConfig(webappDir.toFile());

        // 4. Copy test.zul into webapp dir
        Path testZulSource = Path.of(TEST_ZUL_SOURCE);
        assertTrue(Files.exists(testZulSource),
                "test.zul not found at " + testZulSource);
        Files.copy(testZulSource, webappDir.resolve("test.zul"),
                StandardCopyOption.REPLACE_EXISTING);

        // 5. Start child JVM on port 0 (OS assigns a free port)
        String javaExe = ProcessHandle.current().info().command()
                .orElse(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");

        ProcessBuilder pb = new ProcessBuilder(
                javaExe,
                "-cp", launcherJar.getAbsolutePath(),
                "org.zkoss.zkpreview.ZkPreviewServer",
                "0",                           // port 0 → OS picks a free port
                webappDir.toAbsolutePath().toString(),
                zkClasspath
        );
        pb.redirectErrorStream(true);
        serverProcess = pb.start();

        // 6. Read stdout until READY:{port} or timeout
        serverPort = waitForReady(serverProcess, logFile, 60);
        assertTrue(serverPort > 0,
                "Server did not send READY within timeout. Check " + logFile);
    }

    @Test
    @Order(1)
    void rendersStaticZul() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + "/test.zul"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, response.statusCode(),
                "Expected HTTP 200 from ZK server. Body: " + response.body().substring(0, Math.min(500, response.body().length())));

        String body = response.body();
        assertTrue(body.contains("<html"),
                "Response should contain '<html' (ZK full-page render). Got: " +
                body.substring(0, Math.min(500, body.length())));
        assertTrue(body.contains("Column 1"),
                "Response should contain 'Column 1' (static label from test.zul). Got: " +
                body.substring(0, Math.min(500, body.length())));
    }

    @Test
    @Order(2)
    void noErrorsInLog() throws Exception {
        // Give drainer thread a moment to flush remaining output
        Thread.sleep(500);

        if (!Files.exists(logFile)) {
            // No log file written means no errors were routed — acceptable
            return;
        }

        String log = Files.readString(logFile, StandardCharsets.UTF_8);
        boolean hasError = log.lines().anyMatch(line ->
                line.contains(" ERROR ") || line.contains("Exception"));
        assertFalse(hasError,
                "Server log contains ERROR or Exception.\nLog tail:\n" +
                lastLines(log, 30));
    }

    @AfterAll
    static void stopServer() {
        if (serverProcess != null && serverProcess.isAlive()) {
            serverProcess.destroy();
            try {
                serverProcess.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        // Best-effort cleanup of temp dir
        if (tempDir != null) {
            try {
                Files.walk(tempDir)
                     .sorted(Comparator.reverseOrder())
                     .map(Path::toFile)
                     .forEach(File::delete);
            } catch (IOException ignored) {}
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Runs {@code mvn dependency:build-classpath} on the plugin-test project and
     * returns the resulting OS-separated classpath string.
     */
    private static String resolveZkClasspath() throws IOException, InterruptedException {
        Path cpFile = Files.createTempFile("zk-preview-it-cp-", ".txt");
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
            // Drain output so process doesn't block
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                r.lines().forEach(l -> {}); // consume
            }
            boolean finished = p.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                p.destroy();
                throw new IOException("mvn dependency:build-classpath timed out");
            }
            if (p.exitValue() != 0) {
                throw new IOException("mvn dependency:build-classpath failed (exit " + p.exitValue() + ")");
            }
            return Files.readString(cpFile, StandardCharsets.UTF_8).trim();
        } finally {
            Files.deleteIfExists(cpFile);
        }
    }

    /**
     * Waits up to {@code timeoutSeconds} for the child process to emit a
     * {@code READY:{port}} line. All output is tee'd to {@code logFile}.
     *
     * @return the port number, or -1 on timeout / error
     */
    private static int waitForReady(Process process, Path logFile, int timeoutSeconds)
            throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        int[] foundPort = {-1};

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter log = new PrintWriter(
                     new FileWriter(logFile.toFile(), true), true)) {

            String line;
            while ((line = reader.readLine()) != null) {
                log.println(line);
                ServerEvent event = ServerEvent.parse(line);
                if (event.getKind() == ServerEvent.Kind.READY) {
                    foundPort[0] = event.getPort();
                    // Hand off reader to a drainer so the process doesn't block
                    final BufferedReader r = reader;
                    final PrintWriter l = log;
                    Thread drainer = new Thread(() -> {
                        try {
                            String dl;
                            while ((dl = r.readLine()) != null) {
                                l.println(dl);
                            }
                        } catch (IOException ignored) {}
                    }, "zk-preview-it-drainer");
                    drainer.setDaemon(true);
                    drainer.start();
                    return foundPort[0];
                } else if (event.getKind() == ServerEvent.Kind.ERROR) {
                    return -1;
                }
                if (System.currentTimeMillis() > deadline) {
                    return -1;
                }
            }
        }
        return foundPort[0];
    }

    /** Writes minimal zk.xml and web.xml into {@code webappDir/WEB-INF/}. */
    private static void writeMinimalZkConfig(File webappDir) throws IOException {
        File webInf = new File(webappDir, "WEB-INF");
        webInf.mkdirs();

        Files.writeString(new File(webInf, "zk.xml").toPath(),
                "<zk><system-config>" +
                "<disable-event-thread>true</disable-event-thread>" +
                "</system-config></zk>",
                StandardCharsets.UTF_8);

        Files.writeString(new File(webInf, "web.xml").toPath(),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<web-app xmlns=\"https://jakarta.ee/xml/ns/jakartaee\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"https://jakarta.ee/xml/ns/jakartaee " +
                "https://jakarta.ee/xml/ns/jakartaee/web-app_5_0.xsd\"\n" +
                "         version=\"5.0\">\n" +
                "  <listener>\n" +
                "    <listener-class>org.zkoss.zk.ui.http.HttpSessionListener</listener-class>\n" +
                "  </listener>\n" +
                "  <servlet>\n" +
                "    <servlet-name>zkLoader</servlet-name>\n" +
                "    <servlet-class>org.zkoss.zk.ui.http.DHtmlLayoutServlet</servlet-class>\n" +
                "    <init-param>\n" +
                "      <param-name>update-uri</param-name>\n" +
                "      <param-value>/zkau</param-value>\n" +
                "    </init-param>\n" +
                "    <load-on-startup>1</load-on-startup>\n" +
                "  </servlet>\n" +
                "  <servlet-mapping>\n" +
                "    <servlet-name>zkLoader</servlet-name>\n" +
                "    <url-pattern>*.zul</url-pattern>\n" +
                "  </servlet-mapping>\n" +
                "  <servlet>\n" +
                "    <servlet-name>auEngine</servlet-name>\n" +
                "    <servlet-class>org.zkoss.zk.au.http.DHtmlUpdateServlet</servlet-class>\n" +
                "  </servlet>\n" +
                "  <servlet-mapping>\n" +
                "    <servlet-name>auEngine</servlet-name>\n" +
                "    <url-pattern>/zkau/*</url-pattern>\n" +
                "  </servlet-mapping>\n" +
                "</web-app>",
                StandardCharsets.UTF_8);
    }

    private static String lastLines(String text, int n) {
        String[] lines = text.split("\n");
        int from = Math.max(0, lines.length - n);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < lines.length; i++) {
            sb.append(lines[i]).append('\n');
        }
        return sb.toString();
    }
}
