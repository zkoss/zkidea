package org.zkoss.zkidea.preview;

import org.jetbrains.concurrency.AsyncPromise;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R2-CRIT3 (code review #2): {@code preparePreview} submitted its {@code ReadAction.nonBlocking}
 * target resolution with an {@code .onSuccess} handler and no {@code .onError}. If {@code resolveTarget}
 * threw -- any {@code RuntimeException}: a non-local {@code VirtualFile} (a {@code .zul} opened from
 * inside a jar), a {@code docroot.relativize(zulPath)} mismatch, an already-disposed module during a
 * concurrent Gradle/Maven re-import -- the promise rejected silently, {@code onReady} never fired, and
 * the pane sat on "Starting ZK preview server…" forever. That is the one failure path in this feature
 * that ends in nothing: no message, no Report link, no retry, and no hint that closing and reopening
 * the tab is the only recovery. It is the same "stuck loading" mode {@code startGuarded} (U2) closes
 * one step later in the same flow, and it is tested here the same way -- through the platform-free
 * seam, so no {@code Application} is needed.
 */
class ResolveFailureDeliveryTest {

    @Test
    void aFailedTargetResolutionReachesTheFailureHandlerInsteadOfHangingThePane() {
        AsyncPromise<String> resolution = new AsyncPromise<>();
        AtomicReference<String> resolved = new AtomicReference<>();
        AtomicReference<Throwable> failed = new AtomicReference<>();

        ZulPreviewServerService.wireResolveOutcome(resolution, resolved::set, failed::set);
        resolution.setError(new IllegalStateException("'other' is different type of Path"));

        assertNull(resolved.get(), "a rejected resolution must not reach the success path");
        assertNotNull(failed.get(), "a rejected resolution must be delivered, not swallowed -- "
                + "otherwise the preview pane stays on 'Starting ZK preview server…' forever");
        assertTrue(String.valueOf(failed.get()).contains("'other' is different type of Path"),
                "the original failure must survive to the error card: " + failed.get());
    }

    @Test
    void aSuccessfulTargetResolutionStillReachesTheSuccessHandler() {
        AsyncPromise<String> resolution = new AsyncPromise<>();
        AtomicReference<String> resolved = new AtomicReference<>();
        AtomicReference<Throwable> failed = new AtomicReference<>();

        ZulPreviewServerService.wireResolveOutcome(resolution, resolved::set, failed::set);
        resolution.setResult("target");

        assertEquals("target", resolved.get());
        assertNull(failed.get());
    }

    @Test
    void theDeliveredErrorCardNamesTheRootCauseNotTheWrapper() {
        Throwable wrapped = new RuntimeException("preview target resolution failed",
                new IllegalArgumentException("no docroot for /tmp/scratch/page.zul"));

        String message = PreviewResult.error(ZulPreviewServerService.rootMessage(wrapped)).getMessage();

        assertTrue(message.contains("no docroot for /tmp/scratch/page.zul"),
                "the user needs the root cause, not the wrapper: " + message);
    }
}
