package org.zkoss.zkidea.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link BuildSystemDetector}'s pure label normalisation. The build tool does not
 * change how the preview resolves ZK (the plugin reads IntelliJ's resolved model, never
 * {@code pom.xml}/{@code build.gradle}), but it is what tells us how to reproduce a reported
 * failure and whether the project was imported at all -- see
 * tasks/preview-report-environment-analysis.md §3e. The {@code Module} lookup itself is a thin
 * platform wrapper verified in the IDE.
 */
class BuildSystemDetectorTest {

    @Test
    void normalisesGradlesShoutedExternalSystemId() {
        // IntelliJ's Gradle integration reports the id as "GRADLE"; a bug report shouldn't shout.
        assertEquals("Gradle", BuildSystemDetector.label("GRADLE"));
    }

    @Test
    void keepsMavensAlreadyCapitalisedId() {
        assertEquals("Maven", BuildSystemDetector.label("Maven"));
    }

    @Test
    void reportsNoneForAModuleNoBuildToolImported() {
        // A hand-configured module (jars attached directly as module libraries) has no external
        // system id -- and "no build tool" is itself a distinct support category worth seeing.
        assertEquals("none", BuildSystemDetector.label(null));
        assertEquals("none", BuildSystemDetector.label(""));
        assertEquals("none", BuildSystemDetector.label("   "));
    }

    @Test
    void passesThroughAnyOtherExternalSystemNormalised() {
        assertEquals("Sbt", BuildSystemDetector.label("SBT"));
    }
}
