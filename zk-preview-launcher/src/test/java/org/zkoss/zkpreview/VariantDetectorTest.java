package org.zkoss.zkpreview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * D2 regression (PLAN.md "E3 round 2"): {@link VariantDetector} must deterministically
 * identify the ZK core jar's servlet-API variant even when the classpath handed to it
 * also contains an unrelated jar that happens to bundle a class at the exact same fully
 * qualified path as ZK's own {@code DHtmlLayoutServlet} (e.g. a shaded/uber-jar
 * dependency, or a stale duplicate left on a misconfigured classpath).
 *
 * <p>This became a live risk once the plugin-side classpath handoff was widened from
 * "ZK-prefixed jars only" to "every runtime library jar" (round-2 fix for D1): detection
 * can no longer assume the marker class exists on at most one entry, so a naive
 * first-match-in-whatever-order-the-caller-supplied scan is not deterministic. Before the
 * round-2 fix, {@link VariantDetector#detect} returned whichever candidate happened to
 * come first in the input list; these tests place the decoy first specifically to prove
 * that.
 */
class VariantDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsJakarta_evenWhenAnUnrelatedDecoyJarShadowsTheMarkerClassPathFirst() throws IOException {
        File decoy = writeMarkerClassJar("unrelated-uberjar.jar", "javax/servlet");
        File realZkJar = writeMarkerClassJar("zk-10.1.0-jakarta.jar", "jakarta/servlet");

        ZkVariant variant = VariantDetector.detect(List.of(decoy, realZkJar));

        assertEquals(ZkVariant.JAKARTA, variant);
    }

    @Test
    void detectsJavax_evenWhenAnUnrelatedDecoyJarShadowsTheMarkerClassPathFirst() throws IOException {
        File decoy = writeMarkerClassJar("unrelated-uberjar.jar", "jakarta/servlet");
        File realZkJar = writeMarkerClassJar("zk-9.6.0.2.jar", "javax/servlet");

        ZkVariant variant = VariantDetector.detect(List.of(decoy, realZkJar));

        assertEquals(ZkVariant.JAVAX, variant);
    }

    private File writeMarkerClassJar(String jarFileName, String servletPackageMarker) throws IOException {
        File jarFile = tempDir.resolve(jarFileName).toFile();
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
            jos.putNextEntry(new JarEntry("org/zkoss/zk/ui/http/DHtmlLayoutServlet.class"));
            // Not real bytecode -- VariantDetector only byte-scans for the literal
            // servlet-package marker string, it never loads/parses the class.
            jos.write(("FAKE_CLASS_BYTES " + servletPackageMarker + " padding").getBytes(StandardCharsets.US_ASCII));
            jos.closeEntry();
        }
        return jarFile;
    }
}
