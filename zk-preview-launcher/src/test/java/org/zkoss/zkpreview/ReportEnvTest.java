package org.zkoss.zkpreview;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the environment block the render-error page's "Report this issue on GitHub" link carries
 * ({@link Main#reportEnv}). This is the report path a *render* failure actually takes, and it used
 * to say only which plugin/IDE/OS/JDK were involved -- nothing about how the page was set up to
 * render, which is what a render failure is almost always about
 * (tasks/preview-report-environment-analysis.md).
 *
 * <p>The label set and order are a contract shared with the plugin-side assembler
 * ({@code PreviewIssueReporter.renderEnvironment} / {@code PreviewIssueReporterTest}); the two
 * modules have no compile dependency on each other, so each side locks the same list here.
 */
class ReportEnvTest {

    /** The canonical order, mirrored in {@code PreviewIssueReporterTest}. */
    private static final String[] LABELS =
            {"Plugin:", "IDE:", "OS:", "JDK:", "Build:", "Layout:", "Servlet:", "ZK jars:"};

    private static Map<String, String> opts(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test
    void carriesTheRenderTargetFactsThePluginPassedIn() {
        String env = Main.reportEnv(opts(
                "report-plugin", "ZKIdea 1.0.0",
                "report-ide", "IntelliJ IDEA 2024.3 (IU-243.1)",
                "report-build", "Maven",
                "report-layout", "WAR webapp",
                "report-zkjars", "zk-10.0.0.jar, zkmax-10.0.0.jar [31 classpath entries]"),
                ZkVariant.JAKARTA);

        assertTrue(env.contains("Plugin: ZKIdea 1.0.0"), env);
        assertTrue(env.contains("Build: Maven"), env);
        assertTrue(env.contains("Layout: WAR webapp"), env);
        assertTrue(env.contains("ZK jars: zk-10.0.0.jar, zkmax-10.0.0.jar [31 classpath entries]"), env);
    }

    @Test
    void reportsTheServletVariantItActuallyDetected() {
        // Only the launcher knows this -- it is detected from the ZK core jar's own bytecode, and a
        // mis-detection is a real bug class that was previously invisible in any report.
        String javax = Main.reportEnv(opts("report-plugin", "ZKIdea 1.0.0"), ZkVariant.JAVAX);
        String jakarta = Main.reportEnv(opts("report-plugin", "ZKIdea 1.0.0"), ZkVariant.JAKARTA);

        assertTrue(javax.contains("Servlet: javax"), javax);
        assertTrue(jakarta.contains("Servlet: jakarta"), jakarta);
    }

    @Test
    void ordersLabelsCanonically() {
        String env = Main.reportEnv(opts(
                "report-plugin", "p", "report-ide", "i",
                "report-build", "b", "report-layout", "l", "report-zkjars", "z"),
                ZkVariant.JAKARTA);

        int previous = -1;
        for (String label : LABELS) {
            int at = env.indexOf(label);
            assertTrue(at > previous,
                    () -> "labels must appear in the canonical order shared with the plugin: " + env);
            previous = at;
        }
        assertEquals(LABELS.length, env.split("\n").length, () -> "one line per fact: " + env);
    }

    @Test
    void omitsFactsTheCallerDidNotSupply() {
        // The standalone CLI passes only an identity; unknown facts must vanish, not print as "null".
        String env = Main.reportEnv(opts("report-plugin", "ZKIdea 1.0.0"), null);

        assertFalse(env.contains("Build:"), env);
        assertFalse(env.contains("Layout:"), env);
        assertFalse(env.contains("Servlet:"), env);
        assertFalse(env.contains("ZK jars:"), env);
        assertFalse(env.contains("null"), () -> "a missing value must never print as 'null': " + env);
    }

    @Test
    void staysNullWhenNoPluginOrIdeIdentityWasPassed() {
        // Unchanged behaviour: the standalone CLI with no identity gets no env block at all, so the
        // error page's report link still works but omits it.
        assertNull(Main.reportEnv(opts("report-build", "Maven"), ZkVariant.JAKARTA));
    }

    @Test
    void endsWithoutATrailingNewline_soTheIssueBodyKeepsItsSpacing() {
        String env = Main.reportEnv(opts("report-plugin", "ZKIdea 1.0.0"), ZkVariant.JAKARTA);

        assertFalse(env.endsWith("\n"), () -> "ErrorPageRenderer adds its own separator: [" + env + "]");
    }
}
