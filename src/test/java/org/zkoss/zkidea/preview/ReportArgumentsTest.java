package org.zkoss.zkidea.preview;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the {@code --report-*} flags the plugin hands the launcher so the render-error page can
 * describe the render target (doc/zul_preview_spec.md §2.7).
 *
 * <p>These flag names cross a module boundary that has <b>no shared constant</b>: the plugin emits
 * them here and {@code org.zkoss.zkpreview.Main#reportEnv} reads them by string key on the other
 * side (locked there by {@code ReportEnvTest}). A rename on either side would otherwise silently
 * drop facts from every future bug report -- the launcher would just see an unknown option, omit
 * the line, and carry on. Both sides therefore assert the same literals.
 */
class ReportArgumentsTest {

    @Test
    void emitsEveryReportFlagTheLauncherReadsByName() {
        List<String> args = ZulPreviewServerService.reportArguments(
                "ZKIdea 1.0.0", "IntelliJ IDEA 2024.3 (IU-243.1)",
                "Gradle", "Spring Boot classpath web", "zk-10.0.0.jar [24 classpath entries]");

        assertEquals(List.of(
                "--report-plugin", "ZKIdea 1.0.0",
                "--report-ide", "IntelliJ IDEA 2024.3 (IU-243.1)",
                "--report-build", "Gradle",
                "--report-layout", "Spring Boot classpath web",
                "--report-zkjars", "zk-10.0.0.jar [24 classpath entries]"), args);
    }

    @Test
    void everyFlagIsFollowedByItsValue_soTheLauncherArgParserStaysInStep() {
        // The launcher parses "--key value" pairs positionally; a null/absent value would shift
        // every later flag by one and silently mislabel the report.
        List<String> args = ZulPreviewServerService.reportArguments("p", "i", "none", "WAR webapp", "none [0]");

        assertEquals(0, args.size() % 2, () -> "flags and values must pair up: " + args);
        for (int i = 0; i < args.size(); i += 2) {
            int flag = i;
            assertTrue(args.get(flag).startsWith("--report-"), () -> "expected a flag at " + flag + ": " + args);
            assertTrue(args.get(flag + 1) != null && !args.get(flag + 1).isBlank(),
                    () -> "every flag needs a non-blank value: " + args);
        }
    }
}
