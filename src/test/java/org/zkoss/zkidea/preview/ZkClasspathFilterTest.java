package org.zkoss.zkidea.preview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ZkClasspathFilter} (E3 deliverable 6: pure logic, no IntelliJ
 * platform dependency).
 */
class ZkClasspathFilterTest {

    @TempDir
    Path tempDir;

    @Test
    void recognizesEachDocumentedZkArtifactPrefix() {
        assertTrue(ZkClasspathFilter.isZkJar("zk-10.1.0-jakarta.jar"));
        assertTrue(ZkClasspathFilter.isZkJar("zul-10.1.0-jakarta.jar"));
        assertTrue(ZkClasspathFilter.isZkJar("zkbind-10.0.0.jar"));
        assertTrue(ZkClasspathFilter.isZkJar("zcommon-9.6.4.jar"));
        assertTrue(ZkClasspathFilter.isZkJar("zweb-9.6.4.jar"));
        assertTrue(ZkClasspathFilter.isZkJar("zel-9.6.5.jar"));
        assertTrue(ZkClasspathFilter.isZkJar("zhtml-9.6.4.jar"));
        assertTrue(ZkClasspathFilter.isZkJar("zkmax-10.1.0-jakarta.jar"));
        assertTrue(ZkClasspathFilter.isZkJar("zkex-10.1.0-jakarta.jar"));
        assertTrue(ZkClasspathFilter.isZkJar("zuti-9.6.4.jar"));
        assertTrue(ZkClasspathFilter.isZkJar("zkplus-10.1.0-jakarta.jar"));
    }

    @Test
    void recognizesAddonOnlyJarsAsZkJarsForTheR7Gate() {
        // A module that only depends on a ZK addon (no core zk-*.jar directly on its own
        // classpath signature) must still pass the "does this module have ZK at all" gate.
        // Known gap this test bounds: the gate is a name-prefix list, so an addon named
        // outside it still reads as "no ZK" (see ZkClasspathFilter's prefix constant).
        assertTrue(ZkClasspathFilter.isZkJar("zkcharts-11.0.0.jar"));
        assertTrue(ZkClasspathFilter.isZkJar("zkpivot-3.1.0.jar"));
        assertTrue(ZkClasspathFilter.isZkJar("keikai-6.0.0.jar"));
    }

    @Test
    void isCaseInsensitive() {
        assertTrue(ZkClasspathFilter.isZkJar("ZK-10.1.0-JAKARTA.JAR"));
    }

    @Test
    void rejectsUnrelatedJars() {
        assertFalse(ZkClasspathFilter.isZkJar("guava-31.1-jre.jar"));
        assertFalse(ZkClasspathFilter.isZkJar("jakarta.servlet-api-5.0.0.jar"));
        // Must not false-positive on a jar that merely contains "zk" as a substring.
        assertFalse(ZkClasspathFilter.isZkJar("myzkextension-1.0.jar"));
    }

    @Test
    void filterZkJarsKeepsOnlyZkEntriesAndPreservesAbsolutePaths() {
        List<String> classpath = List.of(
                "/repo/.m2/org/zkoss/zk/zk/10.1.0-jakarta/zk-10.1.0-jakarta.jar",
                "/repo/.m2/com/google/guava/guava/31.1-jre/guava-31.1-jre.jar",
                "/repo/.m2/org/zkoss/zul/zul/10.1.0-jakarta/zul-10.1.0-jakarta.jar");

        List<File> zkJars = ZkClasspathFilter.filterZkJars(classpath);

        assertEquals(2, zkJars.size());
        assertTrue(zkJars.stream().anyMatch(f -> f.getName().equals("zk-10.1.0-jakarta.jar")));
        assertTrue(zkJars.stream().anyMatch(f -> f.getName().equals("zul-10.1.0-jakarta.jar")));
    }

    @Test
    void filterLibraryJarsKeepsEveryFileRegardlessOfName() throws IOException {
        File slf4j = newJar("slf4j-api-1.7.25.jar");
        File guava = newJar("guava-31.1-jre.jar");
        File zk = newJar("zk-10.1.0-jakarta.jar");

        List<File> libraryJars = ZkClasspathFilter.filterLibraryJars(
                List.of(slf4j.getAbsolutePath(), guava.getAbsolutePath(), zk.getAbsolutePath()));

        assertEquals(3, libraryJars.size());
        assertTrue(libraryJars.stream().anyMatch(f -> f.getName().equals("slf4j-api-1.7.25.jar")));
        assertTrue(libraryJars.stream().anyMatch(f -> f.getName().equals("guava-31.1-jre.jar")));
        assertTrue(libraryJars.stream().anyMatch(f -> f.getName().equals("zk-10.1.0-jakarta.jar")));
    }

    @Test
    void filterLibraryJarsExcludesDirectories() throws IOException {
        File moduleOutputDir = tempDir.resolve("classes").toFile();
        moduleOutputDir.mkdirs();
        File zk = newJar("zk-10.1.0-jakarta.jar");

        List<File> libraryJars = ZkClasspathFilter.filterLibraryJars(
                List.of(moduleOutputDir.getAbsolutePath(), zk.getAbsolutePath()));

        assertEquals(1, libraryJars.size());
        assertEquals("zk-10.1.0-jakarta.jar", libraryJars.get(0).getName());
    }

    @Test
    void filterResourceRootsKeepsExistingDirectoriesAndDropsFilesAndMissing() throws IOException {
        // A resource root (e.g. src/main/resources) is a directory -> kept, so the launcher
        // can resolve a user's ~./ pages. A jar is a file -> dropped (that's filterLibraryJars'
        // job). A non-existent path -> dropped defensively. Complement of
        // filterLibraryJarsExcludesDirectories.
        File resourceRoot = tempDir.resolve("resources").toFile();
        assertTrue(resourceRoot.mkdirs(), "test precondition: create a resource-root dir");
        File jar = newJar("zk-10.1.0-jakarta.jar");
        String missing = tempDir.resolve("does-not-exist").toString();

        List<File> roots = ZkClasspathFilter.filterResourceRoots(
                List.of(resourceRoot.getAbsolutePath(), jar.getAbsolutePath(), missing));

        assertEquals(1, roots.size());
        assertEquals(resourceRoot.getAbsolutePath(), roots.get(0).getAbsolutePath());
    }

    @Test
    void filterLibraryJarsExcludesSdkPseudoEntriesAndNonexistentPaths() throws IOException {
        File zk = newJar("zk-10.1.0-jakarta.jar");
        // Regression guard from a real observation: a live launcher process spawned by the
        // real IDE showed JDK module pseudo-entries like this on its classpath, because
        // OrderEnumerator included SDK roots (since fixed at the source with .withoutSdk()).
        // Such an entry is neither a directory nor an openable regular file, so it must be
        // dropped defensively here too.
        String sdkPseudoEntry = "/Library/Java/JavaVirtualMachines/zulu-24.jdk/Contents/Home!/java.base";
        String nonexistentPath = tempDir.resolve("does-not-exist.jar").toString();

        List<File> libraryJars = ZkClasspathFilter.filterLibraryJars(
                List.of(sdkPseudoEntry, nonexistentPath, zk.getAbsolutePath()));

        assertEquals(1, libraryJars.size());
        assertEquals("zk-10.1.0-jakarta.jar", libraryJars.get(0).getName());
    }

    @Test
    void signatureIsStableForTheSameJarSetRegardlessOfOrder() throws IOException {
        File a = newJar("zk-10.1.0-jakarta.jar");
        File b = newJar("zul-10.1.0-jakarta.jar");

        String sig1 = ZkClasspathFilter.signature(List.of(a, b));
        String sig2 = ZkClasspathFilter.signature(List.of(b, a));

        assertEquals(sig1, sig2);
    }

    @Test
    void signatureChangesWhenAJarIsModified() throws IOException, InterruptedException {
        File a = newJar("zk-10.1.0-jakarta.jar");
        String before = ZkClasspathFilter.signature(List.of(a));

        // Ensure a distinguishable mtime/size change regardless of filesystem timestamp
        // resolution.
        Thread.sleep(10);
        Files.writeString(a.toPath(), "changed", java.nio.file.StandardOpenOption.APPEND);
        boolean touched = a.setLastModified(a.lastModified() + 5000);
        assertTrue(touched, "test precondition: must be able to touch mtime");

        String after = ZkClasspathFilter.signature(List.of(a));

        assertNotEquals(before, after);
    }

    @Test
    void signatureChangesWhenTheJarSetChanges() throws IOException {
        File a = newJar("zk-10.1.0-jakarta.jar");
        File b = newJar("zul-10.1.0-jakarta.jar");

        String withOnlyA = ZkClasspathFilter.signature(List.of(a));
        String withBoth = ZkClasspathFilter.signature(List.of(a, b));

        assertNotEquals(withOnlyA, withBoth);
    }

    @Test
    void detectZkPresenceReportsNoneWhenNoZkNamedEntryIsDeclared() {
        // Only non-ZK entries -> the module simply has no ZK dependency.
        assertEquals(ZkClasspathFilter.ZkPresence.NONE,
                ZkClasspathFilter.detectZkPresence(List.of(
                        "/repo/guava-31.1-jre.jar", "/repo/slf4j-api-1.7.25.jar")));
    }

    @Test
    void detectZkPresenceReportsPresentWhenAZkJarExistsOnDisk() throws IOException {
        File zk = newJar("zk-10.1.0-jakarta.jar");
        assertEquals(ZkClasspathFilter.ZkPresence.PRESENT,
                ZkClasspathFilter.detectZkPresence(List.of(zk.getAbsolutePath())));
    }

    @Test
    void detectZkPresenceReportsDeclaredButMissingWhenTheZkJarIsNotOnDisk() {
        // U3: ZK is declared (a zk-*.jar name is on the classpath) but the file is gone -- a wiped
        // local repo cache / dangling path. This must be distinguished from "no ZK dependency": the
        // user has ZK, they just need to re-import/re-sync, not "add a ZK dependency".
        String danglingZk = tempDir.resolve("zk-10.1.0-jakarta.jar").toString(); // never created
        String existingNonZk = tempDir.resolve("guava-31.1-jre.jar").toString();
        assertEquals(ZkClasspathFilter.ZkPresence.DECLARED_BUT_MISSING,
                ZkClasspathFilter.detectZkPresence(List.of(danglingZk, existingNonZk)));
    }

    // --- classpathSummary: the "ZK jars:" line of a GitHub failure report
    //     (doc/zul_preview_spec.md §2.7 -- the resolved ZK jar set is the
    //     single most diagnostic fact about a render failure, and used to be invisible).

    @Test
    void classpathSummary_listsZkJarFileNamesOnly_neverAbsolutePaths() {
        String summary = ZkClasspathFilter.classpathSummary(List.of(
                "/Users/someone/.m2/repository/org/zkoss/zk/zk/10.0.0/zk-10.0.0.jar",
                "/Users/someone/.m2/repository/org/slf4j/slf4j-api/2.0.7/slf4j-api-2.0.7.jar",
                "/Users/someone/.m2/repository/org/zkoss/zk/zul/10.0.0/zul-10.0.0.jar"));

        assertTrue(summary.contains("zk-10.0.0.jar"), summary);
        assertTrue(summary.contains("zul-10.0.0.jar"), summary);
        // Non-ZK jars are noise in the report (and blow the URL budget).
        assertFalse(summary.contains("slf4j"), () -> "only ZK jars belong in the summary: " + summary);
        // Absolute paths are long AND leak the user's home directory -- names only.
        assertFalse(summary.contains("/Users/someone"),
                () -> "absolute paths must never reach a public bug report: " + summary);
    }

    @Test
    void classpathSummary_reportsTheTotalClasspathEntryCount() {
        String summary = ZkClasspathFilter.classpathSummary(List.of(
                "/repo/zk-10.0.0.jar", "/repo/slf4j-api-2.0.7.jar", "/repo/commons-io-2.11.jar"));

        assertTrue(summary.contains("3 classpath entries"),
                () -> "the total entry count tells us how much else was on the classpath: " + summary);
    }

    @Test
    void classpathSummary_capsTheListAndSaysHowManyItDropped() {
        List<String> many = new java.util.ArrayList<>();
        for (int i = 0; i < ZkClasspathFilter.MAX_SUMMARY_JARS + 3; i++) {
            many.add("/repo/zkcharts-" + i + ".0.0.jar");
        }

        String summary = ZkClasspathFilter.classpathSummary(many);

        assertTrue(summary.contains("(+3 more)"),
                () -> "a capped list must say how many it dropped: " + summary);
        assertFalse(summary.contains("zkcharts-" + (ZkClasspathFilter.MAX_SUMMARY_JARS + 2) + ".0.0.jar"),
                () -> "the list must actually be capped: " + summary);
    }

    @Test
    void classpathSummary_saysNoneWhenTheClasspathCarriesNoZkJar() {
        String summary = ZkClasspathFilter.classpathSummary(List.of("/repo/slf4j-api-2.0.7.jar"));

        assertTrue(summary.startsWith("none"),
                () -> "the no-ZK case must read as 'none', not as an empty line: " + summary);
        assertTrue(summary.contains("1 classpath entries"), summary);
    }

    @Test
    void classpathSummary_keepsClasspathOrder_soJarShadowingIsVisible() {
        // A stale duplicate earlier on the classpath shadows the good one; the report must show
        // the order it actually had, not a tidied alphabetical view.
        String summary = ZkClasspathFilter.classpathSummary(List.of(
                "/old/zk-9.6.0.jar", "/new/zk-10.0.0.jar"));

        assertTrue(summary.indexOf("zk-9.6.0.jar") < summary.indexOf("zk-10.0.0.jar"),
                () -> "classpath order must be preserved: " + summary);
    }

    private File newJar(String name) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, "stub");
        return path.toFile();
    }
}
