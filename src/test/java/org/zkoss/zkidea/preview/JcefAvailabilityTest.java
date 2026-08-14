package org.zkoss.zkidea.preview;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When JCEF is unavailable, the pane must tell the user <em>why</em> and how to fix it — for every
 * way it can be unavailable, not just the ones that existed when the feature was written (#66).
 * This locks the pure reason-diagnosis; the actual editor wiring (the message card + the "Open
 * preview in external browser" link) is JCEF/Swing behaviour with no headless seam, verified by
 * hand in {@code ./gradlew runIde} with {@code -Dide.browser.jcef.enabled=false} to force the
 * unavailable path (lesson #1: don't claim a Swing/JCEF card works without seeing it).
 *
 * <p>{@code diagnose(jcefClassesLoadable, jcefEnabledInRegistry, javaVendor)} takes the three probe
 * signals so it stays pure and headless-testable: whether the JCEF classes are reachable at all
 * (from 2026.2 they belong to a bundled plugin the user can disable), the IDE registry toggle
 * {@code ide.browser.jcef.enabled}, and whether the boot runtime is a JetBrains Runtime (up to
 * 2025.2 JCEF shipped only inside the JBR).
 */
class JcefAvailabilityTest {

    private static final String JBR = "JetBrains s.r.o.";

    @Test
    void nonJetBrainsRuntime_isDiagnosedAsBootJdkWithoutJcef_andTellsUserToSwitchRuntime() {
        JcefAvailability.Diagnosis d = JcefAvailability.diagnose(true, true, "Oracle Corporation");

        assertEquals(JcefAvailability.Reason.BOOT_JDK_NO_JCEF, d.getReason());
        String lower = d.getExplanation().toLowerCase(Locale.ROOT);
        assertTrue(lower.contains("jetbrains runtime"),
                () -> "must name the JetBrains Runtime as the fix: " + d.getExplanation());
        assertTrue(lower.contains("boot"),
                () -> "must point at the boot runtime setting to change: " + d.getExplanation());
    }

    @Test
    void otherThirdPartyRuntime_isAlsoBootJdkWithoutJcef() {
        // Amazon Corretto, Temurin, etc. — any non-JBR vendor has no bundled JCEF.
        JcefAvailability.Diagnosis d = JcefAvailability.diagnose(true, true, "Amazon.com Inc.");

        assertEquals(JcefAvailability.Reason.BOOT_JDK_NO_JCEF, d.getReason());
    }

    @Test
    void unreachableClassesOnAJetBrainsRuntime_blamesTheBundledJcefPlugin_andSaysHowToEnableIt() {
        // 2026.2+: the JBR is "nomod" and JCEF lives in the bundled "Web Browser (JCEF)" plugin, so
        // the classes can be missing on a perfectly healthy JetBrains Runtime (#66).
        JcefAvailability.Diagnosis d = JcefAvailability.diagnose(false, true, JBR);

        assertEquals(JcefAvailability.Reason.JCEF_PLUGIN_UNAVAILABLE, d.getReason());
        String explanation = d.getExplanation();
        assertTrue(explanation.contains("Web Browser (JCEF)"),
                () -> "must name the plugin to enable, exactly as the IDE lists it: " + explanation);
        String lower = explanation.toLowerCase(Locale.ROOT);
        assertTrue(lower.contains("plugins"),
                () -> "must point at Settings ▸ Plugins, not the registry or the boot JDK: " + explanation);
        assertTrue(lower.contains("restart"),
                () -> "enabling a plugin needs a restart — say so: " + explanation);
    }

    @Test
    void unreachableClassesOnANonJetBrainsRuntime_stillBlamesTheRuntime() {
        // Both signals are bad; the runtime is the root cause, because enabling any plugin on a
        // non-JBR boot JDK still leaves the older IDEs without JCEF.
        JcefAvailability.Diagnosis d = JcefAvailability.diagnose(false, true, "Eclipse Adoptium");

        assertEquals(JcefAvailability.Reason.BOOT_JDK_NO_JCEF, d.getReason());
    }

    @Test
    void registryDisabledOnAJetBrainsRuntime_isDiagnosedAsDisabled_andNamesTheRegistryKey() {
        JcefAvailability.Diagnosis d = JcefAvailability.diagnose(true, false, JBR);

        assertEquals(JcefAvailability.Reason.REGISTRY_DISABLED, d.getReason());
        String explanation = d.getExplanation();
        assertTrue(explanation.contains("ide.browser.jcef.enabled"),
                () -> "must name the exact registry key to flip: " + explanation);
        assertTrue(explanation.toLowerCase(Locale.ROOT).contains("restart"),
                () -> "enabling the registry key requires a restart — say so: " + explanation);
    }

    @Test
    void missingClassesWin_overTheRegistryToggle() {
        // Flipping a registry key cannot help when the classes aren't on the classloader at all.
        JcefAvailability.Diagnosis d = JcefAvailability.diagnose(false, false, JBR);

        assertEquals(JcefAvailability.Reason.JCEF_PLUGIN_UNAVAILABLE, d.getReason());
    }

    @Test
    void nonJetBrainsRuntimeWins_evenWhenTheRegistryIsAlsoOff() {
        // On a non-JBR JDK, enabling the registry alone would NOT add JCEF — the JDK is the root
        // cause, so guidance must point at the runtime, not send the user chasing the registry.
        JcefAvailability.Diagnosis d = JcefAvailability.diagnose(true, false, "Oracle Corporation");

        assertEquals(JcefAvailability.Reason.BOOT_JDK_NO_JCEF, d.getReason());
    }

    @Test
    void jetBrainsRuntimeWithRegistryOnAndClassesPresent_fallsBackToGenericIncompatible() {
        JcefAvailability.Diagnosis d = JcefAvailability.diagnose(true, true, JBR);

        assertEquals(JcefAvailability.Reason.INCOMPATIBLE, d.getReason());
        assertTrue(d.getExplanation().toLowerCase(Locale.ROOT).contains("incompatible"),
                () -> "the catch-all must still explain JCEF is incompatible with this runtime: "
                        + d.getExplanation());
    }

    @Test
    void browserThatFailsToStart_reportsWhatFailed_ratherThanARemedyThatWouldNotHelp() {
        // Nothing is missing and no setting would change this, so the card has to carry the failure
        // itself — otherwise the pane would sit on "Starting ZK preview server…" with no
        // explanation at all (the R2-CRIT3 dead end, one layer up).
        JcefAvailability.Diagnosis d = JcefAvailability.initializationFailed(
                new IllegalStateException("cef_server did not start"));

        assertEquals(JcefAvailability.Reason.INITIALIZATION_FAILED, d.getReason());
        assertTrue(d.getExplanation().contains("cef_server did not start"),
                () -> "must surface the underlying failure: " + d.getExplanation());
    }

    @Test
    void browserFailureWithoutAMessage_stillNamesTheFailure() {
        JcefAvailability.Diagnosis d = JcefAvailability.initializationFailed(new NoClassDefFoundError());

        assertEquals(JcefAvailability.Reason.INITIALIZATION_FAILED, d.getReason());
        assertTrue(d.getExplanation().contains("NoClassDefFoundError"),
                () -> "a message-less throwable must still be identified by type: " + d.getExplanation());
    }

    @Test
    void everyReasonHasADistinctNonBlankExplanation() {
        List<JcefAvailability.Diagnosis> all = List.of(
                JcefAvailability.diagnose(true, true, "Oracle Corporation"),
                JcefAvailability.diagnose(false, true, JBR),
                JcefAvailability.diagnose(true, false, JBR),
                JcefAvailability.diagnose(true, true, JBR),
                JcefAvailability.initializationFailed(new IllegalStateException("boom")));

        assertEquals(JcefAvailability.Reason.values().length, all.size(),
                "every reason must be covered here — a new one needs its own explanation and remedy");

        Set<String> explanations = new LinkedHashSet<>();
        Set<JcefAvailability.Reason> reasons = new LinkedHashSet<>();
        for (JcefAvailability.Diagnosis d : all) {
            assertNotNull(d.getExplanation());
            assertFalse(d.getExplanation().isBlank(), "explanation must not be blank");
            explanations.add(d.getExplanation());
            reasons.add(d.getReason());
        }
        assertEquals(all.size(), explanations.size(), "each reason must read differently");
        assertEquals(all.size(), reasons.size(), "each case must map to its own reason");
    }

    @Test
    void nullOrBlankVendor_doesNotThrow() {
        // java.vendor is a required system property in practice, but a null/blank probe must
        // never crash the editor constructor — it degrades to a usable diagnosis.
        assertNotNull(JcefAvailability.diagnose(true, true, null).getReason());
        assertNotNull(JcefAvailability.diagnose(true, true, "  ").getReason());
    }
}
