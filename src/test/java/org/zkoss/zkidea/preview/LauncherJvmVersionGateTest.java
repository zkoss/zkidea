package org.zkoss.zkidea.preview;

import com.intellij.openapi.projectRoots.JavaSdkVersion;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The helper JVM is spawned from the project SDK when there is one, and {@code
 * zk-preview-launcher.jar} is Java 17 bytecode -- so a project SDK older than 17 cannot even load
 * the launcher's main class. That shipped: a project configured with a JDK 11 SDK failed every
 * preview with
 *
 * <pre>
 * java.lang.UnsupportedClassVersionError: org/zkoss/zkpreview/Main has been compiled by a more
 * recent version of the Java Runtime (class file version 61.0), this version of the Java Runtime
 * only recognizes class file versions up to 55.0
 * </pre>
 *
 * <p>and the launcher died before printing a port, surfacing as the generic "exited before it
 * reported a port" card. The old guard only asked whether the project SDK existed and was a
 * {@code JavaSdkType}; a JDK 11 SDK passes both. The missing question -- can this JVM actually
 * load our bytecode -- is {@link ZulPreviewServerService#canRunLauncherJar}, exercised here
 * through its platform-free seam.
 */
class LauncherJvmVersionGateTest {

    @Test
    void aProjectSdkOlderThanTheLauncherBytecodeIsRejected() {
        assertFalse(ZulPreviewServerService.canRunLauncherJar(JavaSdkVersion.JDK_11),
                "JDK 11 reads class files up to 55.0 and cannot load the launcher's 61.0 main class "
                        + "-- this is the exact SDK that shipped the UnsupportedClassVersionError");
        assertFalse(ZulPreviewServerService.canRunLauncherJar(JavaSdkVersion.JDK_1_8),
                "JDK 8 is likewise too old to load the launcher");
    }

    @Test
    void aProjectSdkAtOrAboveTheLauncherBytecodeIsUsed() {
        assertTrue(ZulPreviewServerService.canRunLauncherJar(JavaSdkVersion.JDK_17),
                "JDK 17 matches the launcher's bytecode exactly and must still be preferred");
        assertTrue(ZulPreviewServerService.canRunLauncherJar(JavaSdkVersion.JDK_21),
                "a newer project SDK runs the launcher fine");
    }

    @Test
    void anUnknownProjectSdkVersionFallsBackToTheIdeRuntime() {
        // JavaSdk#getVersion returns null for an SDK whose version string can't be parsed (a broken
        // or half-configured SDK entry). Unknown must mean "don't risk it": the IDE's own runtime is
        // known to be >= 17 for every build this plugin supports (sinceBuild 233.2 / IntelliJ 2023.3).
        assertFalse(ZulPreviewServerService.canRunLauncherJar(null),
                "an unparseable SDK version must fall back to the IDE runtime, not be gambled on");
    }

    /**
     * Locks the gate to the jar it guards. The bug was a silent coupling: {@code
     * zk-preview-launcher/build.gradle}'s {@code targetCompatibility} and the JVM-selection code had
     * no test tying them together, so nothing noticed that the launcher demanded a JDK the selection
     * logic was free to hand it. Raising the launcher's target without raising
     * {@link ZulPreviewServerService#MINIMUM_LAUNCHER_SDK} now fails here instead of in a user's IDE.
     */
    @Test
    void theGateMatchesThePackagedLauncherJarsActualBytecodeLevel() throws Exception {
        File launcherJar = launcherJarFile();
        Assumptions.assumeTrue(launcherJar.isFile(),
                "skip: " + launcherJar.getAbsolutePath() + " not built (run :zk-preview-launcher:jar first)");

        assertEquals(44 + featureVersionOf(ZulPreviewServerService.MINIMUM_LAUNCHER_SDK),
                mainClassFileMajorVersion(launcherJar),
                "the launcher's bytecode level and the minimum project SDK the gate accepts have drifted apart");
    }

    private static File launcherJarFile() {
        String prop = System.getProperty("zkpreview.launcherJar");
        if (prop != null) {
            return new File(prop);
        }
        return new File("zk-preview-launcher/build/libs/zk-preview-launcher.jar").getAbsoluteFile();
    }

    /** {@code JDK_17} -&gt; 17, {@code JDK_1_8} -&gt; 8. */
    private static int featureVersionOf(JavaSdkVersion version) {
        String name = version.name().substring("JDK_".length()).replace("1_", "");
        return Integer.parseInt(name);
    }

    /** The {@code major_version} field of the launcher's main class (Java 17 =&gt; 61). */
    private static int mainClassFileMajorVersion(File jar) throws Exception {
        try (ZipFile zip = new ZipFile(jar);
             InputStream in = zip.getInputStream(zip.getEntry("org/zkoss/zkpreview/Main.class"));
             DataInputStream data = new DataInputStream(in)) {
            assertEquals(0xCAFEBABE, data.readInt(), "not a class file");
            data.readUnsignedShort(); // minor_version
            return data.readUnsignedShort();
        }
    }
}
