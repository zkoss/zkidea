package org.zkoss.zkpreview;

import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L-10 (doc/zul_preview_product_positioning.md §3, P0): a broken ZUL must show a
 * formatted, human-readable error in the pane — never the raw HTTP-500 JSON. This locks
 * the HTML the launcher serves in place of that JSON. The structured {@link RenderError}
 * object is unchanged (see {@link StructuredFailureTest}); only the browser-facing
 * presentation is added here.
 */
class ErrorPageRendererTest {

    private static RenderError composeError(String message) {
        return new RenderError(RenderPhase.COMPOSE, message, "/cases/tree-mvvm.zul", null, null);
    }

    @Test
    void rendersHtmlDocument_notJson() {
        String html = ErrorPageRenderer.render(composeError("Class not found: com.example.MyViewModel"));
        String trimmed = html.trim();

        assertFalse(trimmed.startsWith("{"),
                () -> "error page must be HTML, not the raw JSON the browser used to paint: " + trimmed);
        assertTrue(trimmed.startsWith("<"), () -> "error page must be an HTML document: " + trimmed);
        assertTrue(trimmed.toLowerCase(Locale.ROOT).contains("<html"),
                () -> "error page must be a full HTML document: " + trimmed);
    }

    @Test
    void showsPhaseMessageAndFile() {
        String html = ErrorPageRenderer.render(composeError("Class not found: com.example.MyViewModel"));

        assertTrue(html.contains("COMPOSE"), () -> "error page must show the render phase: " + html);
        assertTrue(html.contains("com.example.MyViewModel"),
                () -> "error page must show the failure message (incl. the missing FQCN): " + html);
        assertTrue(html.contains("/cases/tree-mvvm.zul"),
                () -> "error page must show which .zul failed: " + html);
    }

    @Test
    void framesItAsLayoutPreview_viewModelNotExecuted() {
        String html = ErrorPageRenderer.render(composeError("boom")).toLowerCase(Locale.ROOT);

        assertTrue(html.contains("viewmodel"),
                () -> "error page must reassure the user their ViewModel isn't the cause of a render gap");
        assertTrue(
                html.contains("not executed") || html.contains("isn't executed")
                        || html.contains("doesn't run") || html.contains("does not run")
                        || html.contains("never runs"),
                () -> "error page must state the ViewModel does not run in the Layout Preview: " + html);
    }

    @Test
    void escapesHtmlInMessage_noRawMarkupInjected() {
        String html = ErrorPageRenderer.render(composeError("bad <script>alert(1)</script> & <b>x</b>"));

        assertFalse(html.contains("<script>alert(1)</script>"),
                () -> "message markup must be HTML-escaped, not injected raw: " + html);
        assertTrue(html.contains("&lt;script&gt;"), () -> "message '<' must be escaped: " + html);
        assertTrue(html.contains("&amp;"), () -> "message '&' must be escaped: " + html);
    }

    @Test
    void showsLineWhenPresent() {
        String html = ErrorPageRenderer.render(
                new RenderError(RenderPhase.COMPOSE, "boom", "/a.zul", 42, null));

        assertTrue(html.contains("42"), () -> "error page must show the source line when known: " + html);
    }

    @Test
    void showsStackTraceInCollapsedDetailsWhenPresent() {
        String trace = "org.zkoss.zk.ui.UiException: boom\n"
                + "\tat org.zkoss.zk.Foo.compose(Foo.java:10)\n"
                + "Caused by: java.lang.IllegalState<T>\n";
        String html = ErrorPageRenderer.render(
                new RenderError(RenderPhase.COMPOSE, "boom", "/a.zul", null, null, trace));

        assertTrue(html.contains("<details"), () -> "full trace must live in a <details> disclosure: " + html);
        assertFalse(html.matches("(?s).*<details[^>]*\\bopen\\b.*"),
                () -> "the stack trace <details> must be collapsed by default (no 'open'): " + html);
        assertTrue(html.toLowerCase(Locale.ROOT).contains("stack trace"),
                () -> "the disclosure must be labelled so users know what it is: " + html);
        assertTrue(html.contains("org.zkoss.zk.Foo.compose(Foo.java:10)"),
                () -> "the full trace text must be present: " + html);
        // Trace is HTML-escaped like the message (guard against markup in a frame).
        assertFalse(html.contains("IllegalState<T>"), () -> "trace must be HTML-escaped: " + html);
        assertTrue(html.contains("IllegalState&lt;T&gt;"), () -> "trace '<' must be escaped: " + html);
    }

    @Test
    void noStackTraceDetailsWhenAbsent() {
        String html = ErrorPageRenderer.render(composeError("boom")); // 5-arg -> null trace

        assertFalse(html.contains("<details"),
                () -> "no stack-trace disclosure when there is no trace to show: " + html);
    }

    @Test
    void offersReportToGithubLinkCarryingEnvironment() {
        String html = ErrorPageRenderer.render(
                new RenderError(RenderPhase.COMPOSE, "boom", "/a.zul", null, null, "trace here"),
                "Plugin: ZKIdea 0.8.0\nIDE: IU-243");

        assertTrue(html.contains("github.com/zkoss/zkidea/issues/new"),
                () -> "a failed render must offer a one-click GitHub report: " + html);
        assertTrue(html.toLowerCase(Locale.ROOT).contains("report"),
                () -> "the report link must be labelled: " + html);
        // The environment string is URL-encoded into the issue body; "ZKIdea" survives
        // encoding verbatim, so its presence proves the env reached the report URL.
        assertTrue(html.contains("ZKIdea"),
                () -> "report URL must carry the environment (plugin/IDE): " + html);
        // The href is a valid HTML attribute (query '&' escaped as '&amp;').
        assertTrue(html.contains("issues/new?") && html.contains("&amp;body="),
                () -> "report href must be a valid, HTML-escaped GitHub new-issue URL: " + html);
    }

    @Test
    void offersReportLinkEvenWithoutEnvironment() {
        String html = ErrorPageRenderer.render(composeError("boom")); // 1-arg -> null env

        assertTrue(html.contains("github.com/zkoss/zkidea/issues/new"),
                () -> "report link should be offered even when no env was supplied: " + html);
    }

    @Test
    void reportLinkCarriesTheZulSource() {
        String html = ErrorPageRenderer.render(
                new RenderError(RenderPhase.PARSE, "bad tag", "/a.zul", 7, null, "trace"),
                "Plugin: ZKIdea 0.8.0",
                "<zk><label value='SOURCE_MARKER'/></zk>");

        // The source is URL-encoded into the issue body; "SOURCE_MARKER" survives verbatim.
        assertTrue(html.contains("SOURCE_MARKER"),
                () -> "report URL must carry the .zul source so it can be debugged later: " + html);
    }

    @Test
    void smallReport_usesDirectPrefilledLink_noClipboardButton() {
        String html = ErrorPageRenderer.render(
                new RenderError(RenderPhase.COMPOSE, "boom", "/a.zul", null, null, "short trace"),
                "Plugin: ZKIdea 0.8.0", "<zk/>");

        assertTrue(html.contains("issues/new?") && html.contains("&amp;body="),
                () -> "a small report must stay a one-click prefilled link: " + html);
        assertFalse(html.contains("id=\"copyReport\""),
                () -> "a small report must not need the clipboard fallback: " + html);
    }

    @Test
    void overlongReport_copiesToClipboard_withTheInstructionInTheIssueBodyNotThePane() {
        // A report far past the ~8 KB URL limit (a large .zul); it must NOT be truncated.
        String huge = "<zk>\n" + "  <label value=\"SRC_MARKER\"/>\n".repeat(1500) + "</zk>";
        String html = ErrorPageRenderer.render(
                new RenderError(RenderPhase.PARSE, "bad", "/a.zul", null, null, "trace"),
                "Plugin: ZKIdea 0.8.0", huge);

        // The action is a copy-to-clipboard report link...
        assertTrue(html.contains("id=\"copyReport\""),
                () -> "an over-long report must use the copy-to-clipboard report action: " + html);
        // ...but the IDE pane stays clean -- no explanatory paragraph (user feedback: a
        // non-reporter shouldn't have to read it; it belongs in the issue).
        assertFalse(html.contains("class=\"report-note\""),
                () -> "the 'too large / paste' guidance must NOT appear in the IDE pane: " + html);
        // The guidance is pre-filled into the opened GitHub issue's body instead.
        String encodedNote = URLEncoder.encode(ErrorPageRenderer.CLIPBOARD_NOTE, StandardCharsets.UTF_8);
        assertTrue(html.contains(encodedNote),
                () -> "the paste instruction must be pre-filled into the issue body: " + html);
        // The full report rides on the clipboard, untruncated.
        assertTrue(html.contains("SRC_MARKER"),
                () -> "the full source must be carried in the clipboard payload: " + html);
        assertFalse(html.contains("(truncated)"),
                () -> "the fallback must not truncate -- carrying the full report is the point: " + html);
    }

    // --- report body layout (user feedback): source -> environment -> full stack trace,
    // no redundant "Message:" header (the trace carries it), no empty "Steps to reproduce". ---

    @Test
    void reportBody_ordersSourceThenEnvironmentThenStackTrace() {
        String body = ErrorPageRenderer.reportBody(
                new RenderError(RenderPhase.COMPOSE, "boom", "/a.zul", 7, null, "TRACE_MARKER\n\tat x"),
                "ENV_MARKER",
                "<zk>SRC_MARKER</zk>");

        int src = body.indexOf("SRC_MARKER");
        int env = body.indexOf("ENV_MARKER");
        int trace = body.indexOf("TRACE_MARKER");
        assertTrue(src >= 0 && env >= 0 && trace >= 0, () -> "all three sections must be present: " + body);
        assertTrue(src < env && env < trace,
                () -> "report body order must be source -> environment -> stack trace: " + body);
    }

    @Test
    void reportBody_dropsRedundantMessageHeaderAndEmptyStepsSection() {
        String body = ErrorPageRenderer.reportBody(
                new RenderError(RenderPhase.COMPOSE, "boom", "/a.zul", 7, null, "trace text"),
                "env", "<zk/>");

        // The full stack trace already carries the complete exception message, so the
        // partial "Message:" header is redundant; phase + file stay in the issue title.
        assertFalse(body.contains("Message: boom"),
                () -> "the redundant 'Message:' header must be gone: " + body);
        assertFalse(body.toLowerCase(Locale.ROOT).contains("steps to reproduce"),
                () -> "the empty 'Steps to reproduce' section must be gone: " + body);
    }

    @Test
    void reportBody_keepsTheFullStackTrace_notTruncatedAtAFixedBudget() {
        // A trace far larger than the old fixed 1500-char trace budget, but within the
        // overall body cap -- it must be carried in full (the user wants the complete trace).
        String trace = "org.zkoss.zk.ui.UiException: boom\n"
                + "\tat frame.method(File.java:10)\n".repeat(60);
        String body = ErrorPageRenderer.reportBody(
                new RenderError(RenderPhase.COMPOSE, "boom", "/a.zul", null, null, trace), null, null);

        assertTrue(body.contains(trace),
                () -> "the complete stack trace must be carried (no per-field truncation): " + body);
        assertFalse(body.contains("(truncated)"),
                () -> "a trace within the body cap must not be truncated: " + body);
    }
}
