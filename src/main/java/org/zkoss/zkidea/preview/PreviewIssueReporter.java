package org.zkoss.zkidea.preview;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.extensions.PluginId;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds and opens a prefilled GitHub new-issue when a preview can't be displayed, so the
 * user can report it in one click (positioning doc §4 / user request). Reuses the plugin's
 * existing {@link BrowserUtil#browse} feedback pattern; the user always reviews and submits
 * on GitHub — nothing is posted automatically. The {@code .zul} source is inlined (budgeted)
 * so the failure can be debugged later.
 *
 * <p>The URL/body building ({@link #issueUrl}/{@link #body}) is pure and unit-tested; the
 * environment probe and {@link #report} are thin platform wrappers verified in the IDE.
 */
final class PreviewIssueReporter {

    private static final String NEW_ISSUE_URL = "https://github.com/zkoss/zkidea/issues/new";

    /** Body pre-cap (chars, before encoding): a cheap guard so we never URL-encode a multi-megabyte
     *  stack trace. The real length guarantee is {@link #MAX_URL_CHARS}, applied to the encoded URL. */
    static final int MAX_BODY_CHARS = 6000;

    /** Whole-URL cap (chars, <em>after</em> encoding). Dense ZUL markup ({@code < > " =}, newlines)
     *  expands ~3x under URL-encoding, so a pre-encoding char cap alone let the final URL balloon past
     *  GitHub's request-URI limit (HTTP 414) and browser limits — "Report on GitHub" then silently
     *  failed to open (review M2). The full, untruncated report is always shown in the preview pane
     *  (and is copyable), so the prefilled URL is a best-effort convenience kept reliably openable. */
    static final int MAX_URL_CHARS = 8000;
    private static final int SOURCE_BUDGET = 3500;

    private PreviewIssueReporter() {
    }

    private static final String TRUNCATION_NOTE =
            "\n\n…(truncated — see the preview pane for the full details)";

    /** Pure: the prefilled new-issue URL. The body is capped on its <em>encoded</em> length so the
     *  whole URL stays within {@link #MAX_URL_CHARS} regardless of how much markup expands under
     *  encoding (review M2), and everything is URL-encoded. */
    static String issueUrl(String title, String body) {
        String raw = body.length() > MAX_BODY_CHARS
                ? body.substring(0, MAX_BODY_CHARS) + TRUNCATION_NOTE
                : body;
        String prefix = NEW_ISSUE_URL + "?title=" + enc(title) + "&body=";
        int bodyBudget = MAX_URL_CHARS - prefix.length();
        if (enc(raw).length() <= bodyBudget) {
            return prefix + enc(raw);
        }
        // Encoded body still over budget (dense markup expanded past the char cap): shrink the RAW
        // body until its encoded form plus the (encoded) note fits, then re-encode. Truncating the
        // encoded string directly could split a "%XX" escape and corrupt the URL.
        String fitted = fitToEncodedLength(raw, bodyBudget - enc(TRUNCATION_NOTE).length()) + TRUNCATION_NOTE;
        return prefix + enc(fitted);
    }

    /** Longest prefix of {@code raw} whose URL-encoded length is {@code <= budget} (binary search;
     *  encoded length is non-decreasing in prefix length). */
    private static String fitToEncodedLength(String raw, int budget) {
        if (budget <= 0) {
            return "";
        }
        int lo = 0, hi = raw.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (enc(raw.substring(0, mid)).length() <= budget) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return raw.substring(0, lo);
    }

    /** Pure: assemble the issue body from the environment + the failure context. */
    static String body(String context, String environment) {
        return body(context, environment, null);
    }

    /** Pure: the issue body, ordered <em>source → environment → failure detail</em> (user
     * feedback). The {@code .zul} source (budgeted, fenced) leads so a failure can be
     * debugged later; the empty "Steps to reproduce" prompt is intentionally omitted. */
    static String body(String context, String environment, String zulSource) {
        StringBuilder sb = new StringBuilder();
        if (zulSource != null && !zulSource.isBlank()) {
            String src = zulSource.length() <= SOURCE_BUDGET ? zulSource
                    : zulSource.substring(0, SOURCE_BUDGET) + "\n…(truncated)";
            sb.append("ZUL source:\n```xml\n").append(src).append("\n```\n\n");
        }
        sb.append("---\n").append(environment).append("\n\n");
        sb.append("---\n").append(context).append('\n');
        return sb.toString();
    }

    /** Current plugin / IDE / OS / JDK, formatted for the report. */
    static String environment() {
        return "Plugin: ZKIdea " + pluginVersion()
                + "\nIDE: " + ideDescription()
                + "\nOS: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + "\nJDK: " + System.getProperty("java.version");
    }

    /** Open a prefilled GitHub issue for a preview failure in the system browser (with the
     * {@code .zul} source inlined for later debugging; {@code zulSource} may be {@code null}). */
    static void report(String title, String context, String zulSource) {
        BrowserUtil.browse(issueUrl(title, body(context, environment(), zulSource)));
    }

    /** Plugin version (e.g. {@code 0.8.0}); also passed to the launcher for the error-page report link. */
    static String pluginVersion() {
        IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(PluginId.getId("org.zkoss.zkidea"));
        return descriptor != null ? descriptor.getVersion() : "unknown";
    }

    /** IDE name + build (e.g. {@code IntelliJ IDEA 2024.3 (IU-243.x)}); shared with the launcher report link. */
    static String ideDescription() {
        ApplicationInfo app = ApplicationInfo.getInstance();
        return app.getFullApplicationName() + " (" + app.getBuild().asString() + ")";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
