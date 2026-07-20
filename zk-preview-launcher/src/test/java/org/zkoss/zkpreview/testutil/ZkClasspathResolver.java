package org.zkoss.zkpreview.testutil;

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

/**
 * Resolves a real ZK classpath for tests, via {@code mvn dependency:build-classpath}.
 * Never throws: {@link Resolution#jars} is null (with {@link Resolution#skipReason}
 * populated) when resolution isn't possible in this environment, so tests can skip
 * cleanly instead of false-failing on machines without network/Maven access.
 */
public final class ZkClasspathResolver {

    public static final class Resolution {
        public final List<File> jars;
        public final String skipReason;

        private Resolution(List<File> jars, String skipReason) {
            this.jars = jars;
            this.skipReason = skipReason;
        }
    }

    private ZkClasspathResolver() {
    }

    private static volatile Resolution cachedJakarta;
    private static volatile Resolution cachedJavax;

    /** Jakarta variant (ZK 10.1.0-jakarta): resolved against the in-repo manual-test/pom.xml. Memoized per JVM. */
    public static synchronized Resolution resolveJakarta() {
        if (cachedJakarta != null) return cachedJakarta;
        File pom = new File(repoRoot(), "manual-test/pom.xml");
        if (!pom.isFile()) {
            return cachedJakarta = skip("manual-test/pom.xml not found at " + pom.getAbsolutePath());
        }
        return cachedJakarta = runMvnBuildClasspath(pom, false);
    }

    /**
     * Javax variant: prefers the real ZK 9.x javax.servlet project at
     * {@code ~/Documents/workspace/SUPPORT/zk9support} (its jars are typically already
     * cached locally, so this needs no network) and falls back to a throwaway pom hitting
     * ZK's free CE Maven repo (ZK CE 9.6.0.2) when that project isn't present, e.g. on a
     * clean checkout of this repo alone. Memoized per JVM.
     */
    public static synchronized Resolution resolveJavax() {
        if (cachedJavax != null) return cachedJavax;

        File zk9supportPom = new File(System.getProperty("user.home"),
                "Documents/workspace/SUPPORT/zk9support/pom.xml");
        if (zk9supportPom.isFile()) {
            Resolution r = runMvnBuildClasspath(zk9supportPom, false);
            if (r.jars != null) return cachedJavax = r;
        }

        try {
            Path pom = Files.createTempFile("zkpreview-javax-probe-", ".xml");
            Files.writeString(pom, JAVAX_PROBE_POM, StandardCharsets.UTF_8);
            Resolution r = runMvnBuildClasspath(pom.toFile(), true);
            Files.deleteIfExists(pom);
            return cachedJavax = r;
        } catch (IOException e) {
            return cachedJavax = skip("Could not write throwaway probe pom: " + e);
        }
    }

    private static Resolution runMvnBuildClasspath(File pom, boolean online) {
        File mvn = findMvn();
        if (mvn == null) {
            return skip("mvn executable not found on PATH");
        }
        try {
            Path cpFile = Files.createTempFile("zkpreview-cp-", ".txt");
            List<String> cmd = new ArrayList<>(List.of(
                    mvn.getAbsolutePath(), "-f", pom.getAbsolutePath(),
                    "dependency:build-classpath",
                    "-Dmdep.outputFile=" + cpFile.toAbsolutePath(), "-q"));
            ProcessBuilder pb = new ProcessBuilder(cmd);
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
                return skip("mvn dependency:build-classpath timed out");
            }
            if (p.exitValue() != 0) {
                Files.deleteIfExists(cpFile);
                return skip("mvn dependency:build-classpath exited " + p.exitValue() + ":\n" + out);
            }
            String cp = Files.readString(cpFile, StandardCharsets.UTF_8).trim();
            Files.deleteIfExists(cpFile);
            if (cp.isBlank()) {
                return skip("mvn dependency:build-classpath produced an empty classpath");
            }
            List<File> jars = new ArrayList<>();
            for (String entry : cp.split(File.pathSeparator)) {
                if (!entry.isBlank()) jars.add(new File(entry.trim()));
            }
            return new Resolution(jars, null);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return skip("mvn invocation failed: " + e);
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

    private static Resolution skip(String reason) {
        return new Resolution(null, reason);
    }

    private static File repoRoot() {
        String prop = System.getProperty("zkpreview.repoRoot");
        if (prop != null) return new File(prop);
        // Fallback when not run through Gradle: walk up from the working directory.
        File dir = new File("").getAbsoluteFile();
        while (dir != null) {
            if (new File(dir, "settings.gradle").isFile()) return dir;
            dir = dir.getParentFile();
        }
        return new File(".");
    }

    private static final String JAVAX_PROBE_POM =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n" +
            "  <modelVersion>4.0.0</modelVersion>\n" +
            "  <groupId>org.zkoss.zkpreview</groupId>\n" +
            "  <artifactId>javax-zk-probe</artifactId>\n" +
            "  <version>1.0-SNAPSHOT</version>\n" +
            "  <packaging>pom</packaging>\n" +
            "  <dependencies>\n" +
            "    <dependency>\n" +
            "      <groupId>org.zkoss.zk</groupId>\n" +
            "      <artifactId>zkbind</artifactId>\n" +
            "      <version>9.6.0.2</version>\n" +
            "    </dependency>\n" +
            "    <dependency>\n" +
            "      <groupId>javax.servlet</groupId>\n" +
            "      <artifactId>javax.servlet-api</artifactId>\n" +
            "      <version>4.0.1</version>\n" +
            "      <scope>provided</scope>\n" +
            "    </dependency>\n" +
            "  </dependencies>\n" +
            "  <repositories>\n" +
            "    <repository>\n" +
            "      <id>ZK CE</id>\n" +
            "      <name>ZK CE Repository</name>\n" +
            "      <url>https://mavensync.zkoss.org/maven2</url>\n" +
            "    </repository>\n" +
            "  </repositories>\n" +
            "</project>\n";
}
