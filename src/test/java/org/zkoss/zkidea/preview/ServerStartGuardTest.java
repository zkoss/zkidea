package org.zkoss.zkidea.preview;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * U2 (code review): the helper-server start path must never let an exception escape without a
 * deliverable outcome. The command line is assembled from platform lookups
 * ({@code resolveLauncherJar}, which throws when the plugin descriptor is null; {@code
 * resolveJavaExecutable}); if any of that throws, the preview pane used to stay on "loading"
 * forever with no error and no Report link. {@code startGuarded} converts any such throw into a
 * "failed" server whose {@code portFuture} completes exceptionally, so the caller always reaches
 * its error/Report path. Exercised directly via the platform-free supplier seam.
 */
class ServerStartGuardTest {

    @Test
    void aThrowingCommandLineSupplierYieldsAFailedServerNotAnEscape() throws Exception {
        ManagedPreviewServer server = ZulPreviewServerService.startGuarded(() -> {
            throw new IllegalStateException("could not locate the plugin descriptor");
        });

        assertNotNull(server, "startGuarded must always return a server, never throw");
        assertFalse(server.isAlive(), "a failed server has no live process");
        try {
            server.portFuture().get(5, TimeUnit.SECONDS);
            fail("the port future must complete exceptionally when the command line can't be built");
        } catch (ExecutionException expected) {
            assertTrue(String.valueOf(expected.getCause()).contains("could not locate the plugin descriptor"),
                    "the original failure must be preserved as the cause: " + expected.getCause());
        } catch (TimeoutException e) {
            fail("the port future should fail immediately, not hang", e);
        }
    }
}
