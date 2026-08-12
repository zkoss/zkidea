package org.zkoss.zkidea.preview;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationInfo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

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

    /** Current plugin / IDE / OS / JDK, with no render-target facts (nothing was resolved yet). */
    static String environment() {
        return environment(null, null, null);
    }

    /**
     * The environment block including the render target: which build tool imported the module,
     * which docroot rule matched, and which ZK jars resolved. Those three are what actually
     * explain a failed render -- the plugin/IDE/OS/JDK alone never did
     * (doc/zul_preview_spec.md §2.7). Any fact that could not be determined is
     * passed as {@code null} and omitted.
     */
    static String environment(String buildSystem, String layout, String zkJars) {
        return renderEnvironment("ZKIdea " + pluginVersion(), ideDescription(),
                System.getProperty("os.name") + " " + System.getProperty("os.version"),
                System.getProperty("java.version"),
                // No servlet variant here: these reports fire when the preview can't be *displayed*,
                // which is before (or instead of) any launcher run, so nothing detected one. The
                // launcher's own report path fills it in.
                buildSystem, layout, null, zkJars);
    }

    /**
     * Pure: the environment block, one {@code "Label: value"} per line, skipping any value that
     * could not be determined (a blank fact must vanish, never print as an empty or {@code "null"}
     * line). GitHub renders single newlines as line breaks, so no padding or fencing is needed.
     *
     * <p><b>The label set and order here are a contract</b> shared with the launcher's own
     * assembler ({@code org.zkoss.zkpreview.Main#reportEnv}), so the same failure reads identically
     * whichever of the two report paths produced it. The modules have no compile dependency on each
     * other; {@code PreviewIssueReporterTest} and {@code ReportEnvTest} each lock the same list.
     */
    static String renderEnvironment(String plugin, String ide, String os, String jdk,
                                    String buildSystem, String layout, String servlet, String zkJars) {
        StringBuilder sb = new StringBuilder();
        appendFact(sb, "Plugin", plugin);
        appendFact(sb, "IDE", ide);
        appendFact(sb, "OS", os);
        appendFact(sb, "JDK", jdk);
        appendFact(sb, "Build", buildSystem);
        appendFact(sb, "Layout", layout);
        appendFact(sb, "Servlet", servlet);
        appendFact(sb, "ZK jars", zkJars);
        return sb.toString();
    }

    private static void appendFact(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(label).append(": ").append(value);
    }

    /**
     * Open a prefilled GitHub issue for a preview failure in the system browser (with the
     * {@code .zul} source inlined for later debugging; {@code zulSource} may be {@code null}).
     * {@code environment} is the render-target-aware block built when the preview target resolved;
     * when the failure happened before that (so there is none), the plugin/IDE/OS/JDK block stands
     * in.
     */
    static void report(String title, String context, String zulSource, String environment) {
        String env = (environment == null || environment.isBlank()) ? environment() : environment;
        BrowserUtil.browse(issueUrl(title, body(context, env, zulSource)));
    }

    /**
     * Plugin version (e.g. {@code 0.8.0}); also passed to the launcher for the error-page report link.
     *
     * <p>Read from a resource stamped by {@code processResources} rather than from our own plugin
     * descriptor: {@code PluginManagerCore.getPlugin} is {@code @ApiStatus.Internal} in the 2026.2
     * platform and the Marketplace compatibility check rejects it, and every descriptor lookup that
     * could replace it is internal too (see {@code tasks/internal-api-fix-plan.md}). The version is a
     * build-time constant, so baking it in needs no platform lookup at all -- which also makes it
     * correct under unit tests, where there is no {@code Application}.
     */
    static String pluginVersion() {
        try (InputStream in = PreviewIssueReporter.class.getResourceAsStream("plugin-version.properties")) {
            if (in == null) {
                return "unknown";
            }
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("version", "unknown");
        } catch (IOException e) {
            return "unknown";
        }
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
