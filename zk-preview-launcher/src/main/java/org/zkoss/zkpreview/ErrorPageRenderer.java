package org.zkoss.zkpreview;

/**
 * Renders a {@link RenderError} as a self-contained, theme-aware HTML page for the preview
 * pane, shown in place of the raw HTTP-500 JSON the browser used to paint verbatim (L-10,
 * doc/zul_preview_product_positioning.md §3). Dependency-free (no template/JSON library);
 * every interpolated field is HTML-escaped. The structured {@link RenderError} object and
 * its {@code toJson()} are unaffected — this only changes what the browser receives.
 */
public final class ErrorPageRenderer {

    private ErrorPageRenderer() {
    }

    public static String render(RenderError error) {
        String phase = escape(String.valueOf(error.getPhase()));
        String message = escape(error.getMessage());
        String location = location(error);
        String trace = error.getStackTrace();
        String details = (trace == null || trace.isBlank()) ? ""
                : "<details class=\"trace\"><summary>Show full stack trace</summary>"
                + "<pre>" + escape(trace) + "</pre></details>\n";

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
            + ".note{font-size:.82rem;opacity:.72;margin:0}"
            + "@media(prefers-color-scheme:dark){body{color:#dfe1e5;background:#1e1f22}"
            + ".card{background:#2b2d30;border-color:#43454a}.phase{background:#43454a;color:#cfd2d6}"
            + ".msg,details.trace>pre{background:#1e1f22;border-color:#43454a}}";
}
