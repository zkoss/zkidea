package org.zkoss.zkidea.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Locks the pure URL/body building for the "Report this issue on GitHub" action (the
 * feature the user asked for: when a preview can't be shown, report it to GitHub). The
 * runtime bits ({@code environment()}, {@code report()} → {@code BrowserUtil.browse}) are
 * thin platform wrappers verified in the IDE; the encoding + length cap are locked here.
 */
class PreviewIssueReporterTest {

    @Test
    void issueUrl_pointsAtZkideaNewIssue_withEncodedTitleAndBody() {
        String url = PreviewIssueReporter.issueUrl("COMPOSE error in foo.zul", "phase: COMPOSE\nmsg: boom & <x>");

        assertTrue(url.startsWith("https://github.com/zkoss/zkidea/issues/new?"),
                () -> "must open a new issue on the plugin repo: " + url);
        assertTrue(url.contains("title="), url);
        assertTrue(url.contains("body="), url);
        // Query is URL-encoded: no raw spaces, newlines, or reserved chars leak through.
        String query = url.substring(url.indexOf('?') + 1);
        assertFalse(query.contains(" "), () -> "spaces must be encoded: " + url);
        assertFalse(query.contains("\n"), () -> "newlines must be encoded: " + url);
        assertFalse(query.contains("<"), () -> "markup must be encoded: " + url);
    }

    @Test
    void issueUrl_capsBodyLengthSoTheUrlStaysUsable() {
        String hugeBody = "x".repeat(PreviewIssueReporter.MAX_BODY_CHARS + 5000);

        String url = PreviewIssueReporter.issueUrl("t", hugeBody);

        // The decoded body can't exceed the cap (+ a short truncation marker) -- guards
        // against a stack trace blowing past browser/GitHub URL limits.
        int bodyStart = url.indexOf("body=") + "body=".length();
        String encodedBody = url.substring(bodyStart);
        assertTrue(encodedBody.length() < (PreviewIssueReporter.MAX_BODY_CHARS + 400) * 3,
                () -> "over-long body must be truncated before encoding, was " + encodedBody.length());
        assertTrue(url.contains("truncated") || encodedBody.contains("truncated"),
                "a truncated body must say so");
    }

    @Test
    void body_carriesContextEnvironmentAndAReproSection() {
        String body = PreviewIssueReporter.body("phase: PARSE\nmessage: bad tag", "Plugin: 0.8.0\nIDE: IU-243");

        assertTrue(body.contains("phase: PARSE"), body);
        assertTrue(body.contains("message: bad tag"), body);
        assertTrue(body.contains("Plugin: 0.8.0"), body);
        assertTrue(body.contains("IDE: IU-243"), body);
        assertTrue(body.toLowerCase().contains("reproduce") || body.toLowerCase().contains("steps"),
                () -> "body should invite the user to add repro steps: " + body);
    }
}
