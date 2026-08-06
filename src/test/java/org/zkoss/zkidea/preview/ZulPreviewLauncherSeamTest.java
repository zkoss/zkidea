package org.zkoss.zkidea.preview;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plugin&lt;-&gt;launcher seam test. Builds a classpath the exact same way
 * {@link ZulPreviewServerService} does (by calling {@link ZkClasspathFilter}'s real
 * filtering logic on manual-test's real Maven-resolved runtime classpath, including a
 * fake module-output directory the way {@code OrderEnumerator} would report one), then
 * spawns the REAL packaged {@code zk-preview-launcher.jar} with that classpath and
 * asserts it actually boots and serves a page.
 *
 * <p>This is the seam E1's own tests never exercised: E1's {@code IsolationChildProcessTest}
 * fed the launcher the FULL {@code mvn dependency:build-classpath} output directly, never
 * routed through the plugin's classpath filter -- so it could not have caught the plugin
 * dropping every non-{@code org.zkoss} jar (including {@code slf4j-api}, which ZK's
 * {@code WebManager} requires at class-init time), the round-1 manual-gate defect (D1).
 *
 * <p>Network-free after the one-time classpath resolution below (memoized per JVM);
 * skips cleanly (not a failure) if {@code mvn} or network access to resolve
 * manual-test's dependencies isn't available in this environment.
 */
class ZulPreviewLauncherSeamTest {

    private static List<String> cachedManualTestClasspath;
    private static String cachedSkipReason;

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
    void realPackagedLauncherServesAPageWithThePluginsRealFilteredClasspath(@TempDir Path tempDir) throws Exception {
        List<String> rawClasspath = resolveManualTestClasspath();
        Assumptions.assumeTrue(rawClasspath != null, cachedSkipReason);

        File launcherJar = launcherJarFile();
        Assumptions.assumeTrue(launcherJar.isFile(),
                "skip: " + launcherJar.getAbsolutePath() + " not built (run :zk-preview-launcher:jar first)");

        // A fake module-output directory, exactly like OrderEnumerator would report for
        // the previewed module's own compiled classes -- must never reach the launcher
        // (AC-4(i): no output dirs on the render classpath, ever).
        Path fakeModuleOutputDir = Files.createDirectory(tempDir.resolve("fake-module-output"));
        Files.writeString(fakeModuleOutputDir.resolve("UserViewModel.class"), "not a real class file");

        List<String> entriesAsOrderEnumeratorWouldReportThem = new ArrayList<>(rawClasspath);
        entriesAsOrderEnumeratorWouldReportThem.add(fakeModuleOutputDir.toString());

        // NOTE: production code (ZulPreviewServerService#resolveTarget) calls
        // ZkClasspathFilter.filterLibraryJars -- the SAME call this test makes -- to build the
        // handoff classpath. An earlier version called filterZkJars, which dropped every
        // non-org.zkoss jar (including slf4j-api). Running this test against filterZkJars
        // reproduces that shipped crash exactly: NoClassDefFoundError org.slf4j.LoggerFactory
        // raised from WebManager.<clinit> during the launcher's ZK bootstrap. That is the RED
        // this test was written against, and re-pointing it at filterZkJars reproduces it.
        List<File> libraryJars = ZkClasspathFilter.filterLibraryJars(entriesAsOrderEnumeratorWouldReportThem);

        assertFalse(libraryJars.stream().anyMatch(f -> f.getAbsolutePath().equals(fakeModuleOutputDir.toString())),
                "the fake module-output directory must be excluded from the launcher handoff");
        assertTrue(libraryJars.stream().anyMatch(f -> f.getName().startsWith("slf4j-api-")),
                "slf4j-api (a non-ZK-prefixed transitive dependency ZK's WebManager requires at bootstrap) "
                        + "must be included -- this is exactly what D1 got wrong");

        String classpathArg = libraryJars.stream().map(File::getAbsolutePath)
                .reduce((a, b) -> a + File.pathSeparator + b).orElse("");

        Path webapp = Path.of("manual-test/src/main/webapp").toAbsolutePath();

        ProcessBuilder pb = new ProcessBuilder(javaExecutable(), "-jar", launcherJar.getAbsolutePath(),
                "--classpath", classpathArg, "--webapp", webapp.toString(), "--port", "0");
        pb.redirectErrorStream(true);
        serverProcess = pb.start();

        int port = waitForReadyPort(serverProcess, 60);
        assertTrue(port > 0, "zk-preview-launcher exited before it reported a port -- see stderr above");

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + port + "/preview/button.zul"))
                        .timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("zkmx("), "expected a ZK bootstrap marker in the response: " + response.body());
    }

    private static synchronized List<String> resolveManualTestClasspath() {
        if (cachedManualTestClasspath != null || cachedSkipReason != null) {
            return cachedManualTestClasspath;
        }
        File mvn = findMvn();
        if (mvn == null) {
            cachedSkipReason = "skip: mvn executable not found on PATH";
            return null;
        }
        File pom = new File("manual-test/pom.xml").getAbsoluteFile();
        if (!pom.isFile()) {
            cachedSkipReason = "skip: manual-test/pom.xml not found at " + pom;
            return null;
        }
        try {
            Path cpFile = Files.createTempFile("zkidea-seam-cp-", ".txt");
            ProcessBuilder pb = new ProcessBuilder(mvn.getAbsolutePath(), "-f", pom.getAbsolutePath(),
                    "dependency:build-classpath", "-Dmdep.outputFile=" + cpFile.toAbsolutePath(), "-q");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line).append('\n');
            }
            boolean done = p.waitFor(180, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                cachedSkipReason = "skip: mvn dependency:build-classpath timed out";
                return null;
            }
            if (p.exitValue() != 0) {
                cachedSkipReason = "skip: mvn dependency:build-classpath exited " + p.exitValue() + ": " + out;
                return null;
            }
            String cp = Files.readString(cpFile, StandardCharsets.UTF_8).trim();
            Files.deleteIfExists(cpFile);
            if (cp.isBlank()) {
                cachedSkipReason = "skip: mvn dependency:build-classpath produced an empty classpath";
                return null;
            }
            List<String> entries = new ArrayList<>();
            for (String entry : cp.split(File.pathSeparator)) {
                if (!entry.isBlank()) entries.add(entry.trim());
            }
            return cachedManualTestClasspath = entries;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            cachedSkipReason = "skip: mvn invocation failed: " + e;
            return null;
        }
    }

    private static File findMvn() {
        String[] candidates = {"/Applications/maven-3.9.1/bin/mvn", "/usr/local/bin/mvn", "/usr/bin/mvn"};
        for (String c : candidates) {
            File f = new File(c);
            if (f.isFile()) return f;
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                File f = new File(dir, "mvn");
                if (f.isFile()) return f;
            }
        }
        return null;
    }

    private static File launcherJarFile() {
        String prop = System.getProperty("zkpreview.launcherJar");
        if (prop != null) return new File(prop);
        return new File("zk-preview-launcher/build/libs/zk-preview-launcher.jar").getAbsoluteFile();
    }

    private static String javaExecutable() {
        return ProcessHandle.current().info().command()
                .orElse(System.getProperty("java.home") + "/bin/java");
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
