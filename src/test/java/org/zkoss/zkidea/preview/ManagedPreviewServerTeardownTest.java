package org.zkoss.zkidea.preview;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * E3-G2 (teardown): verifies that {@link ManagedPreviewServer#destroy()} actually kills
 * the underlying OS process, without spawning a real zk-preview-launcher JVM -- a
 * short-lived stand-in process ({@code sleep}/{@code timeout}) is enough to prove the
 * kill path {@link ZulPreviewServerService#dispose()} relies on for "no orphan JVMs
 * after project close".
 */
class ManagedPreviewServerTeardownTest {

    @Test
    void destroyTerminatesTheUnderlyingProcess() throws Exception {
        GeneralCommandLine commandLine = SystemInfo.isWindows
                ? new GeneralCommandLine("cmd", "/c", "timeout", "/t", "60")
                : new GeneralCommandLine("sleep", "60");

        ManagedPreviewServer server = new ManagedPreviewServer(commandLine);
        server.start();
        try {
            assertTrue(server.isAlive(), "the stand-in process should be running right after start");

            server.destroy();

            boolean terminated = server.awaitTermination(10, TimeUnit.SECONDS);
            assertTrue(terminated, "destroy() must terminate the process within 10s");
            assertFalse(server.isAlive(), "the process must be reported dead after termination");
        } finally {
            // Safety net in case an assertion above fails before destroy() ran.
            server.destroy();
        }
    }

    @Test
    void portFutureFailsWhenTheProcessNeverStarts() throws InterruptedException {
        // A path that cannot possibly be an executable simulates a launch failure
        // (e.g. a misconfigured project SDK) without touching the filesystem for a
        // fake launcher jar.
        GeneralCommandLine commandLine = new GeneralCommandLine("/nonexistent/definitely-not-a-binary");

        assertDoesNotThrowConstruction(commandLine);
    }

    private void assertDoesNotThrowConstruction(GeneralCommandLine commandLine) throws InterruptedException {
        try {
            ManagedPreviewServer server = new ManagedPreviewServer(commandLine);
            server.start();
            try {
                server.portFuture().get(10, TimeUnit.SECONDS);
                fail("expected the port future to fail for a non-existent executable");
            } catch (ExecutionException expected) {
                // expected: the process never produced PREVIEW_PORT=<n> before terminating/failing.
            } catch (TimeoutException e) {
                fail("port future should have failed promptly, not hung", e);
            } finally {
                server.destroy();
            }
        } catch (com.intellij.execution.ExecutionException e) {
            // Also acceptable: some platforms fail synchronously at process-creation time.
        }
    }
}
