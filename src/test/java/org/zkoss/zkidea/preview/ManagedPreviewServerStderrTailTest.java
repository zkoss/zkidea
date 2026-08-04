package org.zkoss.zkidea.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L2 (code review): the stderr tail retained by {@link ManagedPreviewServer} must be bounded
 * <em>as it is appended</em>, not only trimmed lazily at read time -- otherwise a long-lived
 * helper JVM's steady non-fatal stderr chatter accumulates in memory for the process's whole
 * life. Exercises the pure {@code appendBounded} seam directly (no spawned process needed).
 */
class ManagedPreviewServerStderrTailTest {

    @Test
    void appendBoundedNeverGrowsPastTheCapAndKeepsTheLastCharacters() {
        StringBuilder buf = new StringBuilder();
        int limit = 2000;
        StringBuilder everythingEverAppended = new StringBuilder();

        // A workday of chatter: far more total than the cap, delivered in many small chunks.
        for (int i = 0; i < 1000; i++) {
            String chunk = "stderr chunk #" + i + " — some diagnostic noise\n";
            ManagedPreviewServer.appendBounded(buf, chunk, limit);
            everythingEverAppended.append(chunk);
            assertTrue(buf.length() <= limit,
                    () -> "retained buffer must never exceed the cap on append, was " + buf.length());
        }

        String all = everythingEverAppended.toString();
        assertEquals(all.substring(all.length() - limit), buf.toString(),
                "the retained tail must be exactly the last <limit> characters actually written");
    }

    @Test
    void appendBoundedLeavesUndercapContentIntact() {
        StringBuilder buf = new StringBuilder();
        ManagedPreviewServer.appendBounded(buf, "short", 2000);
        ManagedPreviewServer.appendBounded(buf, " tail", 2000);
        assertEquals("short tail", buf.toString());
    }
}
