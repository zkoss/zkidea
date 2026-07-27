package org.zkoss.zkpreview;

import org.junit.jupiter.api.Test;

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
}
