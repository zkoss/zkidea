package org.zkoss.zkidea.preview;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks how the bundled {@code zk-preview-launcher.jar} is located.
 *
 * <p>It used to be found via {@code PluginManagerCore.getPlugin(...).getPluginPath()}, which the
 * Marketplace compatibility check rejects: {@code getPlugin} became {@code @ApiStatus.Internal} in
 * the 2026.2 platform, and this plugin declares no {@code untilBuild} upper bound. There is no
 * supported replacement for "give me my own plugin descriptor", so the path is now derived from our
 * own jar instead -- {@code prepareSandbox} packages the launcher jar into the very same
 * {@code <plugin>/lib} directory, so it is always a sibling.
 */
class LauncherJarLocationTest {

    @Test
    void theLauncherJarIsResolvedBesideTheJarWeRunFrom() {
        Path ownJar = Paths.get("/opt/idea/config/plugins/zkidea/lib/zkidea-1.0.0.jar");

        Path launcher = ZulPreviewServerService.launcherJarNextTo(ownJar);

        assertEquals(Paths.get("/opt/idea/config/plugins/zkidea/lib/zk-preview-launcher.jar"), launcher,
                "the launcher jar is packaged into the same <plugin>/lib directory as our own jar");
    }

    @Test
    void anUnlocatableOwnJarThrowsSoTheFailureReachesTheErrorCard() {
        // PathManager.getJarForClass is @Nullable. A null (or parent-less) result must throw inside
        // the guarded command-line supplier -- that is what turns it into the preview error+Report
        // card (see ServerStartGuardTest) instead of a pane stuck on "loading".
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ZulPreviewServerService.launcherJarNextTo(null));

        assertTrue(e.getMessage().contains("org.zkoss.zkidea"),
                () -> "the failure must name the plugin whose installation could not be located: " + e.getMessage());
    }
}
