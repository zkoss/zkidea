package org.zkoss.zkpreview;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-2 / E1-G4: the rendering core has zero IntelliJ dependency and zero imports
 * from the plugin's other packages -- it is a standalone module (build.gradle
 * applies only the 'java' plugin) invocable from a plain JUnit test.
 */
class CoreIndependenceTest {

    private static final Pattern FORBIDDEN_IMPORT =
            Pattern.compile("^import\\s+(com\\.intellij\\.|org\\.jetbrains\\.|org\\.zkoss\\.zkidea\\.).*");

    @Test
    void mainSourceHasNoForbiddenImports() throws IOException {
        Path mainSrc = Path.of("src/main/java");
        assertTrue(Files.isDirectory(mainSrc), "src/main/java must exist: " + mainSrc.toAbsolutePath());

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(mainSrc)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    if (FORBIDDEN_IMPORT.matcher(lines.get(i).trim()).matches()) {
                        violations.add(file + ":" + (i + 1) + ": " + lines.get(i).trim());
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "Forbidden imports found in the core module:\n"
                + String.join("\n", violations));
    }

    @Test
    void hooksSourceHasNoForbiddenImports() throws IOException {
        Path hooksSrc = Path.of("src/hooks/java");
        assertTrue(Files.isDirectory(hooksSrc), "src/hooks/java must exist: " + hooksSrc.toAbsolutePath());

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(hooksSrc)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    if (FORBIDDEN_IMPORT.matcher(lines.get(i).trim()).matches()) {
                        violations.add(file + ":" + (i + 1) + ": " + lines.get(i).trim());
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "Forbidden imports found in the hooks module:\n"
                + String.join("\n", violations));
    }

    @Test
    void renderEntryPointIsCallableFromPlainJUnitWithNoIntelliJFixture() throws Exception {
        // Deliberately calls the public entry point with no ApplicationManager/Project/fixture
        // of any kind -- if this test itself needs no IntelliJ platform bootstrap (and it plainly
        // doesn't, being a bare JUnit 5 test class), the core is demonstrably standalone.
        List<File> zkJars = List.of(); // an empty/garbage classpath is fine: we only assert it *runs*.
        try {
            RenderEngineFactory.create(zkJars, Path.of("."));
        } catch (Exception expected) {
            // Expected to fail (no ZK jars) -- the point is that calling it needed
            // no com.intellij.* class on the classpath at all, which this test's own
            // classpath (JUnit + this module only) already proves by construction.
        }
    }

    @Test
    void jdepsReportsNoIntelliJTargets() throws IOException, InterruptedException {
        File jdeps = findJdeps();
        Assumptions.assumeTrue(jdeps != null, "skip: jdeps executable not found");

        Path classesDir = Path.of(System.getProperty("zkpreview.moduleDir", "."), "build/classes/java/main");
        Assumptions.assumeTrue(Files.isDirectory(classesDir),
                "skip: " + classesDir.toAbsolutePath() + " not built yet");

        ProcessBuilder pb = new ProcessBuilder(jdeps.getAbsolutePath(), "-verbose:class", "-filter:none",
                classesDir.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) out.append(line).append('\n');
        }
        boolean done = p.waitFor(60, TimeUnit.SECONDS);
        assertTrue(done, "jdeps timed out");
        assertEquals(0, p.exitValue(), "jdeps exited non-zero:\n" + out);
        assertTrue(!out.toString().contains("com.intellij"), "jdeps reported a com.intellij.* target:\n" + out);
    }

    private static File findJdeps() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            File f = new File(javaHome, "bin/jdeps");
            if (f.isFile()) return f;
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                File f = new File(dir, "jdeps");
                if (f.isFile()) return f;
            }
        }
        return null;
    }
}
