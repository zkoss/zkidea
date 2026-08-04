package org.zkoss.zkpreview;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.zkoss.zkpreview.testutil.ZkClasspathResolver;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AC-4(i)/(ii), strongest form: runs the actual packaged CLI jar as a genuinely
 * separate OS process, whose classpath is exactly {@code zk-preview-launcher.jar}
 * plus the caller-supplied {@code --classpath} -- so the canary class (compiled
 * only into this module's OWN test output, never passed via --classpath) is not
 * merely blocked by an in-test tracker, it is completely absent from that
 * process's classpath. Mirrors the existing spike's proven pattern
 * (ZkPreviewServerIntegrationTest) but against fixture (b), the isolation fixture.
 */
class IsolationChildProcessTest {

    private static final String LAUNCHER_JAR = System.getProperty("zkpreview.moduleDir", ".")
            + "/build/libs/zk-preview-launcher.jar";

    private Process serverProcess;

    @AfterEach
    void stopServer() {
        if (serverProcess != null && serverProcess.isAlive()) {
            serverProcess.destroy();
            try {
                serverProcess.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void realChildProcessNeverLoadsCanaryClassAndRendersPlaceholder() throws Exception {
        File jar = new File(LAUNCHER_JAR);
        Assumptions.assumeTrue(jar.isFile(), "skip: " + jar.getAbsolutePath() + " not built (run the jar task)");

        ZkClasspathResolver.Resolution res = ZkClasspathResolver.resolveJakarta();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        StringBuilder cp = new StringBuilder();
        for (File f : res.jars) {
            if (cp.length() > 0) cp.append(File.pathSeparator);
            cp.append(f.getAbsolutePath());
        }

        Path fixturesDir = Paths.get("src/test/resources/fixtures").toAbsolutePath();
        String javaExe = ProcessHandle.current().info().command()
                .orElse(System.getProperty("java.home") + "/bin/java");

        // Note: this process's -cp is exactly the launcher jar. The canary class
        // (org.zkoss.zkpreview.testcanary.CanaryViewModel) lives only in this
        // module's build/classes/java/test, which is NOT on this command line at
        // all -- unlike the in-process tests, there is no leaky parent classloader
        // to guard against here; the class is genuinely nowhere on this process.
        ProcessBuilder pb = new ProcessBuilder(javaExe, "-jar", jar.getAbsolutePath(),
                "--classpath", cp.toString(), "--webapp", fixturesDir.toString(), "--port", "0");
        pb.redirectErrorStream(true);
        serverProcess = pb.start();

        int port = waitForReadyPort(serverProcess, 60);
        assertTrue(port > 0, "server did not print PREVIEW_PORT within timeout");

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + port + "/viewmodel-bind.zul"))
                        .timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("static sibling"), response.body());
        assertFalse(response.body().contains("LOADED"), "canary value must not leak: " + response.body());
        assertFalse(response.body().contains("CANARY"), "canary value must not leak: " + response.body());
    }

    private static int waitForReadyPort(Process process, int timeoutSeconds) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("PREVIEW_PORT=")) {
                    return Integer.parseInt(line.substring("PREVIEW_PORT=".length()).trim());
                }
                if (System.currentTimeMillis() > deadline) break;
            }
        }
        return -1;
    }
}
