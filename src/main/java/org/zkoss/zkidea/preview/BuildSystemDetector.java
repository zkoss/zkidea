package org.zkoss.zkidea.preview;

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.module.Module;

import java.util.Locale;

/**
 * Names the build tool a module was imported with, for a preview failure's GitHub issue.
 *
 * <p>The build tool is <em>not</em> on the preview's code path -- the plugin never reads
 * {@code pom.xml}/{@code build.gradle} and drives everything off IntelliJ's already-resolved
 * project model, so a Maven and a Gradle project with the same dependencies render identically.
 * It earns its line in the report for three other reasons
 * (doc/zul_preview_spec.md §2.7): it says what kind of skeleton project to
 * build when reproducing, it distinguishes the hand-configured / not-imported project as its own
 * support category, and Maven's and Gradle's IntelliJ importers genuinely differ in how they
 * expose runtime and provided scope -- which is the next question to ask whenever the reported
 * jar list looks wrong.
 */
final class BuildSystemDetector {

    /** Reported when a module was not imported from any build tool (jars attached by hand). */
    private static final String NONE = "none";

    private BuildSystemDetector() {
    }

    /** The build tool behind {@code module}, or {@code "none"} (also for a file outside any module). */
    static String detect(Module module) {
        if (module == null) {
            return NONE;
        }
        return label(ExternalSystemModulePropertyManager.getInstance(module).getExternalSystemId());
    }

    /**
     * Pure: normalises an IntelliJ external-system id for display. Gradle reports {@code "GRADLE"}
     * ({@code GradleConstants.SYSTEM_ID}) and Maven reports {@code "Maven"}
     * ({@code SerializationConstants.MAVEN_EXTERNAL_SOURCE_ID}), so the ids are capitalised
     * consistently rather than shouted into a bug report. A module imported from no build tool
     * has no id at all ({@code getExternalSystemId()} is nullable).
     */
    static String label(String externalSystemId) {
        if (externalSystemId == null || externalSystemId.isBlank()) {
            return NONE;
        }
        String trimmed = externalSystemId.trim();
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT)
                + trimmed.substring(1).toLowerCase(Locale.ROOT);
    }
}
