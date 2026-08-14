package org.zkoss.zkidea.preview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks what {@link ZulPreviewServerService#launcherClasspath} hands the helper JVM on
 * {@code --classpath}: library jars, then the project's compiled-output roots, then the
 * module's resource roots.
 *
 * <p>The compiled-output part is the fix for tasks/class-not-found.md — a {@code <zscript>}
 * naming one of the project's own classes (the ZK demo's {@code new demo.data.BigList(1000)})
 * used to abort the whole render, because every directory was dropped from the handoff.
 */
class LauncherClasspathTest {

    @TempDir
    Path tempDir;

    @Test
    void includesCompiledOutputRootsSoAZscriptCanResolveTheProjectsOwnClasses() throws IOException {
        File zk = newJar("zk-10.1.0-jakarta.jar");
        File outputDir = newDir("target/classes");

        List<File> classpath = ZulPreviewServerService.launcherClasspath(
                List.of(zk.getAbsolutePath(), outputDir.getAbsolutePath()),
                List.of(zk.getAbsolutePath(), outputDir.getAbsolutePath()),
                List.of());

        assertTrue(classpath.contains(outputDir),
                "the module's compiled output must reach the launcher: " + classpath);
    }

    @Test
    void ordersJarsThenCompiledOutputThenResourceRoots() throws IOException {
        // Jars first so ZK's own bundled web/ resources win over any user name collision;
        // compiled output before the resource roots, mirroring a real container, where
        // WEB-INF/classes IS the compiled output (with the resources already copied into it).
        File zk = newJar("zk-10.1.0-jakarta.jar");
        File outputDir = newDir("target/classes");
        File resourceRoot = newDir("src/main/resources");

        List<File> classpath = ZulPreviewServerService.launcherClasspath(
                List.of(zk.getAbsolutePath(), outputDir.getAbsolutePath()),
                List.of(zk.getAbsolutePath(), outputDir.getAbsolutePath()),
                List.of(resourceRoot.getAbsolutePath()));

        assertEquals(List.of(zk, outputDir, resourceRoot), classpath);
    }

    @Test
    void takesOutputRootsOnlyFromTheProductionOnlyEnumeration() throws IOException {
        // The two entry lists differ on purpose (see resolveTarget): the wider one keeps
        // provided-scope jars that productionOnly() would drop, the production-only one keeps
        // target/test-classes off the render classpath. A directory that appears only in the
        // wider list is therefore NOT compiled output we want -- it is a test-output root.
        File zk = newJar("zk-10.1.0-jakarta.jar");
        File productionOutput = newDir("target/classes");
        File testOutput = newDir("target/test-classes");

        List<File> classpath = ZulPreviewServerService.launcherClasspath(
                List.of(zk.getAbsolutePath(), productionOutput.getAbsolutePath(), testOutput.getAbsolutePath()),
                List.of(zk.getAbsolutePath(), productionOutput.getAbsolutePath()),
                List.of());

        assertEquals(List.of(zk, productionOutput), classpath);
    }

    @Test
    void dropsEntriesThatAreNeitherAnExistingJarNorAnExistingDirectory() throws IOException {
        // Same defensive drop filterLibraryJars documents: OrderEnumerator can report SDK
        // pseudo-entries, and a module that was never built has no output directory on disk.
        File zk = newJar("zk-10.1.0-jakarta.jar");
        String sdkPseudoEntry = "/Library/Java/JavaVirtualMachines/zulu-24.jdk/Contents/Home!/java.base";
        String neverBuilt = tempDir.resolve("never-built/target/classes").toString();

        List<File> classpath = ZulPreviewServerService.launcherClasspath(
                List.of(sdkPseudoEntry, zk.getAbsolutePath(), neverBuilt),
                List.of(sdkPseudoEntry, zk.getAbsolutePath(), neverBuilt),
                List.of(tempDir.resolve("no-such-resource-root").toString()));

        assertEquals(List.of(zk), classpath);
    }

    private File newJar(String name) throws IOException {
        Path jar = tempDir.resolve(name);
        Files.writeString(jar, "fake jar content");
        return jar.toFile();
    }

    private File newDir(String relativePath) {
        File dir = tempDir.resolve(relativePath).toFile();
        assertTrue(dir.mkdirs(), "test precondition: create " + relativePath);
        return dir;
    }
}
