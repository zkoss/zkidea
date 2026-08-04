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
    void issueUrl_capsOnEncodedLength_soDenseMarkupCantBlowPastUrlLimits() {
        // Real ZUL markup: every '<' '>' '"' '=' space and newline percent-encodes to 3+ chars, so a
        // cap measured on RAW length let the ENCODED url balloon ~3x past GitHub's 414 limit / browser
        // limits (M2). The existing "x".repeat filler never exercised this (zero-encoding). Assert the
        // guarantee on the length that actually reaches the browser: the encoded url itself.
        String denseMarkup = "<label value=\"@load(vm.x)\" onClick=\"@command('go')\"/>\n".repeat(2000);

        String url = PreviewIssueReporter.issueUrl("[Layout Preview] Cannot display preview", denseMarkup);

        assertTrue(url.length() <= PreviewIssueReporter.MAX_URL_CHARS,
                () -> "the ENCODED url must stay within MAX_URL_CHARS, was " + url.length());
        assertTrue(url.contains("truncated"),
                () -> "an over-long body must be marked truncated: " + url);
    }

    @Test
    void body_carriesContextAndEnvironment_withoutAnEmptyStepsSection() {
        String body = PreviewIssueReporter.body("phase: PARSE\nmessage: bad tag", "Plugin: 0.8.0\nIDE: IU-243");

        assertTrue(body.contains("phase: PARSE"), body);
        assertTrue(body.contains("message: bad tag"), body);
        assertTrue(body.contains("Plugin: 0.8.0"), body);
        assertTrue(body.contains("IDE: IU-243"), body);
        // The pre-filled "Steps to reproduce: 1." was always empty when the user submitted;
        // it is noise and must be gone (user feedback).
        assertFalse(body.toLowerCase().contains("steps to reproduce"),
                () -> "the empty 'Steps to reproduce' section must be gone: " + body);
    }

    @Test
    void body_ordersSourceThenEnvironmentThenContext() {
        String body = PreviewIssueReporter.body("CTX_MARKER", "ENV_MARKER", "<zk>SRC_MARKER</zk>");

        int src = body.indexOf("SRC_MARKER");
        int env = body.indexOf("ENV_MARKER");
        int ctx = body.indexOf("CTX_MARKER");
        assertTrue(src >= 0 && env >= 0 && ctx >= 0, () -> "all three sections must be present: " + body);
        assertTrue(src < env && env < ctx,
                () -> "report body order must be source -> environment -> error detail: " + body);
    }

    @Test
    void body_inlinesZulSourceInAFencedCodeBlockWhenProvided() {
        String body = PreviewIssueReporter.body("ctx", "env", "<zk><label value='SRC_MARKER'/></zk>");

        assertTrue(body.contains("SRC_MARKER"),
                () -> "source must be inlined so the failure can be debugged later: " + body);
        assertTrue(body.contains("```xml"), () -> "source must be a fenced code block: " + body);
    }

    @Test
    void body_truncatesAnOverlongSource() {
        String body = PreviewIssueReporter.body("ctx", "env", "y".repeat(20000));

        assertTrue(body.contains("truncated"), () -> "an over-long source must be truncated: " + body);
    }
}
