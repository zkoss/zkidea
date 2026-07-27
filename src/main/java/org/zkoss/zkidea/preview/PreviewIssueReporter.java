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
 * on GitHub — nothing is posted automatically, and the ZUL source is never included.
 *
 * <p>The URL/body building ({@link #issueUrl}/{@link #body}) is pure and unit-tested; the
 * environment probe and {@link #report} are thin platform wrappers verified in the IDE.
 */
final class PreviewIssueReporter {

    private static final String NEW_ISSUE_URL = "https://github.com/zkoss/zkidea/issues/new";

    /** Body cap (chars, pre-encoding) so the resulting URL stays within browser/GitHub limits. */
    static final int MAX_BODY_CHARS = 6000;

    private PreviewIssueReporter() {
    }

    /** Pure: the prefilled new-issue URL, with the body capped and everything URL-encoded. */
    static String issueUrl(String title, String body) {
        String capped = body.length() > MAX_BODY_CHARS
                ? body.substring(0, MAX_BODY_CHARS) + "\n\n…(truncated — see the preview pane for the full details)"
                : body;
        return NEW_ISSUE_URL + "?title=" + enc(title) + "&body=" + enc(capped);
    }

    /** Pure: assemble the issue body from the failure context + environment + a repro prompt. */
    static String body(String context, String environment) {
        return context
                + "\n\n---\n" + environment
                + "\n\n---\nSteps to reproduce:\n1. \n";
    }

    /** Current plugin / IDE / OS / JDK, formatted for the report. */
    static String environment() {
        ApplicationInfo app = ApplicationInfo.getInstance();
        return "Plugin: ZKIdea " + pluginVersion()
                + "\nIDE: " + app.getFullApplicationName() + " (" + app.getBuild().asString() + ")"
                + "\nOS: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + "\nJDK: " + System.getProperty("java.version");
    }

    /** Open a prefilled GitHub issue for a preview failure in the system browser. */
    static void report(String title, String context) {
        BrowserUtil.browse(issueUrl(title, body(context, environment())));
    }

    private static String pluginVersion() {
        IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(PluginId.getId("org.zkoss.zkidea"));
        return descriptor != null ? descriptor.getVersion() : "unknown";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
