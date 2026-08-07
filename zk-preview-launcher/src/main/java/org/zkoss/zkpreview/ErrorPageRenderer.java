package org.zkoss.zkpreview;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Renders a {@link RenderError} as a self-contained, theme-aware HTML page for the preview
 * pane, shown in place of the raw HTTP-500 JSON the browser used to paint verbatim (L-10,
 * doc/zul_preview_product_positioning.md §3). Dependency-free (no template/JSON library);
 * every interpolated field is HTML-escaped. The structured {@link RenderError} object and
 * its {@code toJson()} are unaffected — this only changes what the browser receives.
 *
 * <p>The page also offers a one-click "Report this issue on GitHub" link, prefilled with
 * the error + environment (Phase 2b). The plugin routes external links to the system
 * browser; {@code reportEnv} carries the plugin/IDE identity the launcher can't know.
 *
 * <p>When the full report (source + environment + complete stack trace) would exceed a safe
 * GitHub URL length ({@link #MAX_URL_LENGTH}), the report link instead copies the complete
 * report to the clipboard and opens a new issue whose <em>body</em> is pre-filled with a short
 * paste instruction ({@link #CLIPBOARD_NOTE}) — so nothing is truncated, and the guidance is
 * seen only by someone who actually clicks to file (not every reader of the error pane).
 */
public final class ErrorPageRenderer {

    private static final String NEW_ISSUE_URL = "https://github.com/zkoss/zkidea/issues/new";
    /** Pre-filled into the opened issue's body when the report is too large to pre-fill; the
     *  complete report is on the clipboard, so this just tells the user to paste it. */
    static final String CLIPBOARD_NOTE =
            "⚠️ This Layout Preview error report was too large to pre-fill automatically.\n\n"
            + "The full details — the ZUL source, the environment, and the complete stack trace — "
            + "have been copied to your clipboard.\n\n"
            + "**Please paste them below (⌘V / Ctrl+V) before submitting.**\n";
    /** Above this full-URL length a prefilled GitHub link risks a 414 / silent truncation, so we
     *  switch to the clipboard hand-off. The classic web-server request-line limit is ~8 KB
     *  (e.g. Apache {@code LimitRequestLine}), and a measured worst case here -- source and stack
     *  trace both at their caps -- came out at 8,210 chars, i.e. right at that line. Hence the cap
     *  below rather than a comfortable margin: reports that exceed it lose nothing, because the
     *  clipboard path carries the full untruncated body. */
    private static final int MAX_URL_LENGTH = 8000;
    /** The one-line reminder under the report link (user request). Two jobs: say what the report
     *  carries, so nobody has to click to find out what they would be sending, and say the click
     *  only opens a <em>draft</em> — GitHub's own form still stands between it and a filed issue.
     *  True on both paths below: the direct link pre-fills the draft, the clipboard link fills it
     *  by paste (the how-to-paste guidance stays in the issue body, out of this pane). */
    static final String REPORT_HINT =
            "Opens a GitHub draft with your ZUL source, environment and stack trace — "
            + "review and edit it before submitting.";

    private ErrorPageRenderer() {
    }

    public static String render(RenderError error) {
        return render(error, null, null);
    }

    public static String render(RenderError error, String reportEnv) {
        return render(error, reportEnv, null);
    }

    public static String render(RenderError error, String reportEnv, String zulSource) {
        String phase = escape(String.valueOf(error.getPhase()));
        String message = escape(error.getMessage());
        String location = location(error);
        String trace = error.getStackTrace();
        String details = (trace == null || trace.isBlank()) ? ""
                : "<details class=\"trace\"><summary>Show full stack trace</summary>"
                + "<pre>" + escape(trace) + "</pre></details>\n";
        String report = reportSection(error, reportEnv, zulSource);

        return "<!doctype html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "<meta charset=\"utf-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                + "<title>Layout Preview — render error</title>\n"
                + "<style>\n" + STYLE + "</style>\n"
                + "</head>\n"
                + "<body>\n"
                + "<div class=\"card\">\n"
                + "<h1>⚠ This ZUL could not be rendered <span class=\"phase\">" + phase + "</span></h1>\n"
                + "<pre class=\"msg\">" + message + "</pre>\n"
                + (location.isEmpty() ? "" : "<div class=\"loc\">" + location + "</div>\n")
                + details
                + "<p class=\"note\">Binding values are not evaluated and your ViewModel is not executed in the "
                + "Layout Preview. Fix the ZUL (or its classpath) and save to re-render.</p>\n"
                + report
                + "</div>\n"
                + "</body>\n"
                + "</html>\n";
    }

    private static String location(RenderError error) {
        if (error.getZulFile() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder("in ").append(escape(error.getZulFile()));
        if (error.getLine() != null) {
            sb.append(':').append(error.getLine());
            if (error.getColumn() != null) {
                sb.append(':').append(error.getColumn());
            }
        }
        return sb.toString();
    }

    /**
     * The report affordance for the page. If the prefilled new-issue URL fits within
     * {@link #MAX_URL_LENGTH} it is a one-click link carrying the whole report; otherwise the
     * same-looking link instead copies the <em>complete</em> report to the clipboard (never
     * truncated) and opens an issue whose body carries only the short paste instruction. Phase
     * + file live in the title; the user always reviews and submits — nothing is auto-posted.
     */
    private static String reportSection(RenderError error, String reportEnv, String zulSource) {
        String title = reportTitle(error);
        String body = reportBody(error, reportEnv, zulSource);
        String directUrl = NEW_ISSUE_URL + "?title=" + enc(title) + "&body=" + enc(body);
        if (directUrl.length() <= MAX_URL_LENGTH) {
            return "<p class=\"report\"><a href=\"" + escape(directUrl)
                    + "\">Report this issue on GitHub ↗</a></p>\n"
                    + reportHint();
        }
        // Too large to prefill -> the link copies the full body to the clipboard and opens an
        // issue whose body is the paste instruction (the error info rides on the clipboard).
        String fallbackUrl = NEW_ISSUE_URL + "?title=" + enc(title) + "&body=" + enc(CLIPBOARD_NOTE);
        return clipboardReportSection(fallbackUrl, body);
    }

    private static String reportTitle(RenderError error) {
        return "[Layout Preview] " + error.getPhase() + " error"
                + (error.getZulFile() == null ? "" : " rendering " + error.getZulFile());
    }

    /**
     * The issue body, ordered <em>source → environment → full stack trace</em> per user
     * feedback, carried in full (no truncation). The stack trace's first lines already carry
     * the complete exception message, so no separate "Message:" header is emitted (it would
     * be redundant), and the always-empty "Steps to reproduce" prompt is omitted.
     */
    static String reportBody(RenderError error, String reportEnv, String zulSource) {
        StringBuilder body = new StringBuilder();
        if (zulSource != null && !zulSource.isBlank()) {
            body.append("ZUL source:\n```xml\n").append(zulSource).append("\n```\n\n");
        }
        if (reportEnv != null && !reportEnv.isBlank()) {
            body.append("---\n").append(reportEnv).append("\n\n");
        }
        String trace = error.getStackTrace();
        if (trace != null && !trace.isBlank()) {
            body.append("---\nStack trace:\n```\n").append(trace).append("\n```\n");
        } else {
            // No trace captured (synthetic errors): fall back to the message so the report
            // still describes the failure.
            body.append("---\n").append(error.getMessage()).append('\n');
        }
        return body.toString();
    }

    /**
     * The copy-to-clipboard report link, used when the report is too large to prefill. It
     * looks exactly like the direct link (same label), but a click first copies the full body
     * to the clipboard ({@code execCommand('copy')} on a temporary textarea — the classic
     * gesture-driven approach that needs no permission — with the async Clipboard API as a
     * best-effort enhancement) and then lets the anchor navigate to {@code issueUrl}, which the
     * plugin's JCEF handler routes to the system browser. The paste instruction lives in the
     * opened issue's body (see {@link #CLIPBOARD_NOTE}), not in this pane.
     */
    private static String clipboardReportSection(String issueUrl, String body) {
        return "<p class=\"report\"><a href=\"" + escape(issueUrl) + "\" id=\"copyReport\">"
                + "Report this issue on GitHub ↗</a> <span id=\"copyStatus\" class=\"copy-status\"></span></p>\n"
                + "<script>\n"
                + "(function(){\n"
                + "var REPORT=" + jsString(body) + ";\n"
                + "var a=document.getElementById('copyReport'),st=document.getElementById('copyStatus');\n"
                + "a.addEventListener('click',function(){\n"
                + "var ok=false;\n"
                + "try{var ta=document.createElement('textarea');ta.value=REPORT;ta.style.position='fixed';"
                + "ta.style.left='-9999px';document.body.appendChild(ta);ta.focus();ta.select();"
                + "ok=document.execCommand('copy');document.body.removeChild(ta);}catch(e){}\n"
                + "if(navigator.clipboard&&navigator.clipboard.writeText){"
                + "try{navigator.clipboard.writeText(REPORT);ok=true;}catch(e){}}\n"
                + "if(st)st.textContent=ok?' ✓ report copied — paste it into the description':"
                + "' — copy failed; please copy the stack trace above manually';\n"
                + "});\n"
                + "})();\n"
                + "</script>\n"
                + reportHint();
    }

    private static String reportHint() {
        return "<p class=\"report-hint\">" + escape(REPORT_HINT) + "</p>\n";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** A safe double-quoted JS string literal for embedding in an inline {@code <script>};
     *  {@code <}, {@code >} and {@code &} are escaped so the content can't break out. */
    private static String jsString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '<': sb.append("\\u003C"); break;
                case '>': sb.append("\\u003E"); break;
                case '&': sb.append("\\u0026"); break;
                case ' ': sb.append("\\u2028"); break;
                case ' ': sb.append("\\u2029"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    private static final String STYLE =
            ":root{color-scheme:light dark}*{box-sizing:border-box}"
            + "body{margin:0;padding:2rem 1.5rem;line-height:1.55;color:#202124;background:#fff;"
            + "font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,Helvetica,Arial,sans-serif}"
            + ".card{max-width:46rem;margin:0 auto;border:1px solid #e3e5e8;border-radius:12px;"
            + "padding:1.5rem 1.75rem;background:#fafbfc}"
            + "h1{font-size:1.05rem;font-weight:600;margin:0 0 1rem}"
            + ".phase{display:inline-block;margin-left:.4rem;font-size:.68rem;font-weight:700;letter-spacing:.05em;"
            + "vertical-align:middle;padding:.18rem .55rem;border-radius:999px;background:#eceff1;color:#455a64}"
            + ".msg{white-space:pre-wrap;word-break:break-word;font-size:.84rem;margin:0 0 .9rem;padding:.8rem .95rem;"
            + "background:#fff;border:1px solid #e3e5e8;border-radius:8px;"
            + "font-family:ui-monospace,SFMono-Regular,\"SF Mono\",Menlo,Consolas,monospace}"
            + ".loc{font-size:.84rem;opacity:.8;margin-bottom:1.1rem}"
            + "details.trace{margin:.25rem 0 1.1rem}"
            + "details.trace>summary{cursor:pointer;font-size:.82rem;opacity:.85;user-select:none}"
            + "details.trace>pre{white-space:pre-wrap;word-break:break-word;font-size:.78rem;margin:.6rem 0 0;"
            + "padding:.75rem .9rem;background:#fff;border:1px solid #e3e5e8;border-radius:8px;max-height:22rem;"
            + "overflow:auto;font-family:ui-monospace,SFMono-Regular,\"SF Mono\",Menlo,Consolas,monospace}"
            + ".note{font-size:.82rem;opacity:.72;margin:0 0 .6rem}"
            + ".report{font-size:.84rem;margin:0}.report>a{color:#2563eb;text-decoration:none}"
            + ".report>a:hover{text-decoration:underline}"
            + ".report-hint{font-size:.76rem;opacity:.65;margin:.25rem 0 0}"
            + "@media(prefers-color-scheme:dark){body{color:#dfe1e5;background:#1e1f22}"
            + ".report>a{color:#6ea8fe}"
            + ".card{background:#2b2d30;border-color:#43454a}.phase{background:#43454a;color:#cfd2d6}"
            + ".msg,details.trace>pre{background:#1e1f22;border-color:#43454a}}";
}
