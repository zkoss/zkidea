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
 */
public final class ErrorPageRenderer {

    private static final String NEW_ISSUE_URL = "https://github.com/zkoss/zkidea/issues/new";
    private static final int MAX_BODY_CHARS = 6000;
    private static final int SOURCE_BUDGET = 3500;

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
        String report = "<p class=\"report\"><a href=\"" + escape(reportUrl(error, reportEnv, zulSource))
                + "\">Report this issue on GitHub ↗</a></p>\n";

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
     * Prefilled GitHub new-issue URL for this failure. The phase + file live in the title;
     * the body is length-capped (via {@link #cap}) so the URL stays within browser/GitHub
     * limits. The user reviews and submits — nothing is auto-posted.
     */
    private static String reportUrl(RenderError error, String reportEnv, String zulSource) {
        String title = "[Layout Preview] " + error.getPhase() + " error"
                + (error.getZulFile() == null ? "" : " rendering " + error.getZulFile());
        return NEW_ISSUE_URL + "?title=" + enc(title) + "&body=" + enc(cap(reportBody(error, reportEnv, zulSource)));
    }

    /**
     * The (uncapped) issue body, ordered <em>source → environment → full stack trace</em>
     * per user feedback. The stack trace's first lines already carry the complete exception
     * message, so no separate "Message:" header is emitted (it would be redundant), and the
     * always-empty "Steps to reproduce" prompt is omitted. The trace is placed last and
     * carried in full here — {@link #cap} trims only the tail if the whole body overflows,
     * and the pane's {@code <details>} disclosure always shows the complete trace. The
     * {@code .zul} source is budgeted (a prefilled URL can't attach a file), so a huge file
     * can't starve the trace of room.
     */
    static String reportBody(RenderError error, String reportEnv, String zulSource) {
        StringBuilder body = new StringBuilder();
        if (zulSource != null && !zulSource.isBlank()) {
            body.append("ZUL source:\n```xml\n").append(truncate(zulSource, SOURCE_BUDGET)).append("\n```\n\n");
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

    private static String truncate(String s, int budget) {
        return s.length() <= budget ? s : s.substring(0, budget) + "\n…(truncated)";
    }

    private static String cap(String body) {
        return body.length() <= MAX_BODY_CHARS ? body
                : body.substring(0, MAX_BODY_CHARS) + "\n```\n…(truncated — see the preview pane for the full details)";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
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
            + "@media(prefers-color-scheme:dark){body{color:#dfe1e5;background:#1e1f22}"
            + ".report>a{color:#6ea8fe}"
            + ".card{background:#2b2d30;border-color:#43454a}.phase{background:#43454a;color:#cfd2d6}"
            + ".msg,details.trace>pre{background:#1e1f22;border-color:#43454a}}";
}
