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
        // R7 watch item (tasks/zul-preview/PLAN.md §8): a module that only depends on a
        // ZK addon (no core zk-*.jar directly on its own classpath signature) must still
        // pass the "does this module have ZK at all" gate.
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
    void filterLibraryJarsExcludesSdkPseudoEntriesAndNonexistentPaths() throws IOException {
        File zk = newJar("zk-10.1.0-jakarta.jar");
        // D4 (tasks/zul-preview/PLAN.md E3 round 3): a live launcher process spawned by
        // the real IDE showed JDK module pseudo-entries like this on its classpath --
        // OrderEnumerator included SDK roots. Such an entry is neither a directory nor
        // an openable regular file, so it must be dropped defensively here too.
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

    private File newJar(String name) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, "stub");
        return path.toFile();
    }
}
