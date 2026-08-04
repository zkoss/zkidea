package org.zkoss.zkidea.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M3 (code review): the external-link handler leaves loopback preview URLs to load in-pane and sends
 * everything else to the system browser. The {@code localhost} test was a bare {@code startsWith}
 * with no authority boundary, so a look-alike host such as {@code http://localhost.evil.example}
 * matched and was trusted in-pane. A booby-trapped rendered page could thus navigate the JCEF pane to
 * an attacker host that merely starts with "localhost". The predicate must require a boundary
 * ({@code :}, {@code /}, or end-of-string) after {@code localhost}.
 */
class LoopbackPreviewUrlTest {

    @Test
    void aLookAlikeHostIsNotTreatedAsLoopback() {
        assertFalse(ZulPreviewFileEditor.isLoopbackPreviewUrl("http://localhost.evil.example/"),
                "localhost.evil.example must NOT be trusted in-pane");
        assertFalse(ZulPreviewFileEditor.isLoopbackPreviewUrl("http://localhostx"),
                "localhostx must NOT be trusted in-pane");
        assertFalse(ZulPreviewFileEditor.isLoopbackPreviewUrl("http://127.0.0.1.evil.example/"),
                "a host that only starts with 127.0.0.1 must NOT be trusted in-pane");
    }

    @Test
    void genuineLoopbackPreviewUrlsAreTrustedInPane() {
        assertTrue(ZulPreviewFileEditor.isLoopbackPreviewUrl("http://127.0.0.1:52134/preview/foo.zul"));
        assertTrue(ZulPreviewFileEditor.isLoopbackPreviewUrl("http://localhost:8080/foo"));
        assertTrue(ZulPreviewFileEditor.isLoopbackPreviewUrl("http://localhost/foo"));
        assertTrue(ZulPreviewFileEditor.isLoopbackPreviewUrl("http://localhost"));
    }

    @Test
    void externalUrlsAreNotLoopback() {
        assertFalse(ZulPreviewFileEditor.isLoopbackPreviewUrl("https://github.com/zkoss/zkidea/issues"));
        assertFalse(ZulPreviewFileEditor.isLoopbackPreviewUrl("http://example.com/"));
        assertFalse(ZulPreviewFileEditor.isLoopbackPreviewUrl(null));
    }
}
