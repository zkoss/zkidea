package org.zkoss.zkidea.preview;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1 (tasks/zul-preview/PLAN-followups.md): when JCEF is unavailable, tell the user <em>why</em>
 * and how to fix it. This locks the pure reason-diagnosis; the actual editor wiring (the message
 * card + the "Open preview in external browser" link) is JCEF/Swing behaviour verified in runIde
 * (MANUAL-jcef-fallback.md, lesson #1).
 *
 * <p>{@code diagnose(jcefEnabledInRegistry, javaVendor)} takes the two probe signals so it stays
 * pure and headless-testable. The two signals JCEF actually depends on: the IDE registry toggle
 * {@code ide.browser.jcef.enabled}, and whether the boot runtime is a JetBrains Runtime (JCEF ships
 * only with the JBR).
 */
class JcefAvailabilityTest {

    @Test
    void nonJetBrainsRuntime_isDiagnosedAsBootJdkWithoutJcef_andTellsUserToSwitchRuntime() {
        JcefAvailability.Diagnosis d = JcefAvailability.diagnose(true, "Oracle Corporation");

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
        JcefAvailability.Diagnosis d = JcefAvailability.diagnose(true, "Amazon.com Inc.");

        assertEquals(JcefAvailability.Reason.BOOT_JDK_NO_JCEF, d.getReason());
    }

    @Test
    void registryDisabledOnAJetBrainsRuntime_isDiagnosedAsDisabled_andNamesTheRegistryKey() {
        JcefAvailability.Diagnosis d = JcefAvailability.diagnose(false, "JetBrains s.r.o.");

        assertEquals(JcefAvailability.Reason.REGISTRY_DISABLED, d.getReason());
        String explanation = d.getExplanation();
        assertTrue(explanation.contains("ide.browser.jcef.enabled"),
                () -> "must name the exact registry key to flip: " + explanation);
        assertTrue(explanation.toLowerCase(Locale.ROOT).contains("restart"),
                () -> "enabling the registry key requires a restart — say so: " + explanation);
    }

    @Test
    void nonJetBrainsRuntimeWins_evenWhenTheRegistryIsAlsoOff() {
        // On a non-JBR JDK, enabling the registry alone would NOT add JCEF — the JDK is the root
        // cause, so guidance must point at the runtime, not send the user chasing the registry.
        JcefAvailability.Diagnosis d = JcefAvailability.diagnose(false, "Oracle Corporation");

        assertEquals(JcefAvailability.Reason.BOOT_JDK_NO_JCEF, d.getReason());
    }

    @Test
    void jetBrainsRuntimeWithRegistryOn_fallsBackToGenericIncompatible() {
        JcefAvailability.Diagnosis d = JcefAvailability.diagnose(true, "JetBrains s.r.o.");

        assertEquals(JcefAvailability.Reason.INCOMPATIBLE, d.getReason());
        assertTrue(d.getExplanation().toLowerCase(Locale.ROOT).contains("incompatible"),
                () -> "the catch-all must still explain JCEF is incompatible with this runtime: "
                        + d.getExplanation());
    }

    @Test
    void everyReasonHasADistinctNonBlankExplanation() {
        String bootJdk = JcefAvailability.diagnose(true, "Oracle Corporation").getExplanation();
        String registry = JcefAvailability.diagnose(false, "JetBrains s.r.o.").getExplanation();
        String incompatible = JcefAvailability.diagnose(true, "JetBrains s.r.o.").getExplanation();

        for (String text : new String[]{bootJdk, registry, incompatible}) {
            assertNotNull(text);
            assertFalse(text.isBlank(), "explanation must not be blank");
        }
        assertFalse(bootJdk.equals(registry), "each reason must read differently");
        assertFalse(bootJdk.equals(incompatible), "each reason must read differently");
        assertFalse(registry.equals(incompatible), "each reason must read differently");
    }

    @Test
    void nullOrBlankVendor_doesNotThrow() {
        // java.vendor is a required system property in practice, but a null/blank probe must
        // never crash the editor constructor — it degrades to a usable diagnosis.
        assertNotNull(JcefAvailability.diagnose(true, null).getReason());
        assertNotNull(JcefAvailability.diagnose(true, "  ").getReason());
    }
}
