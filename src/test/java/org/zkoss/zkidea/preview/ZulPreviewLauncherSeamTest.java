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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plugin&lt;-&gt;launcher seam test. Builds a classpath the exact same way
 * {@link ZulPreviewServerService} does (by calling its real
 * {@link ZulPreviewServerService#launcherClasspath} on manual-test's real Maven-resolved
 * runtime classpath plus a stand-in module-output directory the way {@code OrderEnumerator}
 * would report one), then spawns the REAL packaged {@code zk-preview-launcher.jar} with that
 * classpath and asserts it actually boots and serves a page.
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

    /**
     * What the probe class returns and {@code preview/zscript-user-class.zul} renders. Kept
     * alphanumeric: ZK's JS encoder escapes a hyphen as {@code \-} in the emitted widget JSON,
     * so a hyphenated value would never match the response verbatim.
     */
    private static final String PROBE_TEXT = "compiledOutputReachedTheRender";

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

        // A stand-in module-output directory, exactly like OrderEnumerator would report for the
        // previewed module's own compiled classes -- holding a REAL compiled class, so this test
        // proves the whole chain: plugin classpath assembly -> --classpath -> ScopedZkClassLoader
        // -> BeanShell resolving one of the project's own classes (tasks/class-not-found.md).
        Path moduleOutputDir = Files.createDirectory(tempDir.resolve("module-output"));
        compileProbeClassInto(moduleOutputDir);

        List<String> entriesAsOrderEnumeratorWouldReportThem = new ArrayList<>(rawClasspath);
        entriesAsOrderEnumeratorWouldReportThem.add(moduleOutputDir.toString());

        // NOTE: production code (ZulPreviewServerService#resolveTarget) calls
        // ZulPreviewServerService.launcherClasspath -- the SAME call this test makes -- to build
        // the handoff classpath. Two shipped defects are reproducible by weakening it here:
        // filterZkJars alone dropped every non-org.zkoss jar (including slf4j-api, which ZK's
        // WebManager requires at class-init time) -> NoClassDefFoundError during ZK bootstrap;
        // filterLibraryJars alone dropped every directory, i.e. the module's compiled output
        // -> the zscript page below fails with "Missing class: preview.probe.ClasspathProbe".
        List<File> launcherClasspath = ZulPreviewServerService.launcherClasspath(
                entriesAsOrderEnumeratorWouldReportThem,
                entriesAsOrderEnumeratorWouldReportThem,
                List.of());

        assertTrue(launcherClasspath.contains(moduleOutputDir.toFile()),
                "the module-output directory must reach the launcher handoff");
        assertTrue(launcherClasspath.stream().anyMatch(f -> f.getName().startsWith("slf4j-api-")),
                "slf4j-api (a non-ZK-prefixed transitive dependency ZK's WebManager requires at bootstrap) "
                        + "must be included -- this is exactly what D1 got wrong");

        String classpathArg = launcherClasspath.stream().map(File::getAbsolutePath)
                .reduce((a, b) -> a + File.pathSeparator + b).orElse("");

        Path webapp = Path.of("manual-test/src/main/webapp").toAbsolutePath();

        ProcessBuilder pb = new ProcessBuilder(javaExecutable(), "-jar", launcherJar.getAbsolutePath(),
                "--classpath", classpathArg, "--webapp", webapp.toString(), "--port", "0");
        pb.redirectErrorStream(true);
        serverProcess = pb.start();

        int port = waitForReadyPort(serverProcess, 60);
        assertTrue(port > 0, "zk-preview-launcher exited before it reported a port -- see stderr above");

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpResponse<String> response = get(client, port, "/preview/button.zul");

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("zkmx("), "expected a ZK bootstrap marker in the response: " + response.body());

        // The reported failure (tasks/class-not-found.md): a <zscript> instantiating one of the
        // project's own classes used to abort the render with
        // "Class or variable not found: ...". It renders once the compiled output is handed over.
        HttpResponse<String> zscriptResponse = get(client, port, "/preview/zscript-user-class.zul");

        assertEquals(200, zscriptResponse.statusCode(), zscriptResponse.body());
        assertTrue(zscriptResponse.body().contains(PROBE_TEXT),
                "the zscript's value from the module's own compiled class must reach the page: "
                        + zscriptResponse.body());
    }

    private static HttpResponse<String> get(HttpClient client, int port, String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * Compiles the class {@code preview/zscript-user-class.zul}'s {@code <zscript>} instantiates
     * into {@code outputDir}, standing in for the previewed module's own compiled output. Compiled
     * here rather than checked in as a {@code .class} so it stays readable and can't go stale
     * against the JDK the tests run on.
     *
     * <p>Uses the {@code javac} binary rather than {@code ToolProvider.getSystemJavaCompiler()}:
     * the latter returns {@code null} in this test JVM, whose system classloader the IntelliJ test
     * framework replaces with {@code com.intellij.util.lang.PathClassLoader}, so the tool's
     * {@code ServiceLoader} lookup finds no provider.
     */
    private static void compileProbeClassInto(Path outputDir) throws IOException, InterruptedException {
        Path source = Files.createDirectories(outputDir.resolve("src")).resolve("ClasspathProbe.java");
        Files.writeString(source, "package preview.probe;\n"
                + "public class ClasspathProbe {\n"
                + "    public String text() { return \"" + PROBE_TEXT + "\"; }\n"
                + "}\n", StandardCharsets.UTF_8);

        Path javac = Path.of(javaExecutable()).resolveSibling("javac");
        Assumptions.assumeTrue(Files.isExecutable(javac),
                "skip: no javac next to the test JVM (" + javac + ") -- running on a JRE?");

        ProcessBuilder pb = new ProcessBuilder(javac.toString(),
                "-d", outputDir.toString(), source.toString());
        pb.redirectErrorStream(true);
        Process javacProcess = pb.start();
        String output = new String(javacProcess.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(javacProcess.waitFor(60, TimeUnit.SECONDS), "javac timed out compiling the probe class");
        assertEquals(0, javacProcess.exitValue(), "failed to compile the probe class: " + output);
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
