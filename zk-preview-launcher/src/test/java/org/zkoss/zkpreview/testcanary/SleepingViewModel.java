package org.zkoss.zkpreview.testcanary;

/**
 * A controller that overruns any sane budget, for the timeout half of P0-2 item 6. Sibling of
 * {@link FailingViewModel} and a ViewModel for the same reason (no ZK type is needed to be one).
 *
 * <p>Sleeps rather than busy-loops so the launcher's {@code cancel(true)}/{@code shutdownNow}
 * interrupt actually lands and the abandoned thread ends; the interrupt flag is restored so
 * nothing downstream mistakes this for a normal return.
 */
public class SleepingViewModel {

    public SleepingViewModel() {
        try {
            Thread.sleep(60_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public String getGreeting() {
        return "NEVER-REACHED";
    }
}
