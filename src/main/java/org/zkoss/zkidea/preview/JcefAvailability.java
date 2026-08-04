package org.zkoss.zkidea.preview;

import com.intellij.openapi.util.registry.Registry;

import java.util.Locale;

/**
 * Explains <em>why</em> the embedded browser (JCEF) is unavailable and how the user can fix it,
 * so the Layout Preview can give targeted guidance instead of one generic "JCEF not supported"
 * message (P1, tasks/zul-preview/PLAN-followups.md).
 *
 * <p>JCEF availability ({@code JBCefApp.isSupported()}) depends on two things a user can actually
 * change: the IDE registry toggle {@code ide.browser.jcef.enabled}, and whether the IDE's boot
 * runtime is a JetBrains Runtime — JCEF is bundled only with the JBR, so a boot JDK pointed at a
 * third-party runtime (Oracle, Temurin, Corretto, …) has no JCEF at all.
 *
 * <p>{@link #diagnose(boolean, String)} is pure (it takes those two signals as arguments) so the
 * reason mapping is unit-tested headlessly; {@link #diagnose()} is the thin live probe that reads
 * the registry and the JVM vendor. The resulting card + external-browser link is JCEF/Swing
 * behaviour verified in runIde (MANUAL-jcef-fallback.md).
 */
final class JcefAvailability {

    /** The IDE registry key that turns the embedded browser on/off. */
    static final String JCEF_REGISTRY_KEY = "ide.browser.jcef.enabled";

    enum Reason {
        /** The boot runtime is not a JetBrains Runtime, so no JCEF is bundled. */
        BOOT_JDK_NO_JCEF,
        /** JCEF is present but switched off via the registry. */
        REGISTRY_DISABLED,
        /** JCEF should be there but isn't usable (e.g. an incompatible bundled build). */
        INCOMPATIBLE
    }

    static final class Diagnosis {
        private final Reason reason;
        private final String explanation;

        private Diagnosis(Reason reason, String explanation) {
            this.reason = reason;
            this.explanation = explanation;
        }

        Reason getReason() {
            return reason;
        }

        /** The user-facing why + how-to-fix text for this reason. */
        String getExplanation() {
            return explanation;
        }
    }

    private JcefAvailability() {
    }

    /**
     * Pure reason mapping from the two probe signals. The boot-JDK check comes first: on a
     * non-JBR runtime, enabling the registry alone would not add JCEF, so the runtime is the
     * root cause and the guidance must point there rather than sending the user to the registry.
     */
    static Diagnosis diagnose(boolean jcefEnabledInRegistry, String javaVendor) {
        if (!isJetBrainsRuntime(javaVendor)) {
            return new Diagnosis(Reason.BOOT_JDK_NO_JCEF,
                    "The Layout Preview needs the embedded browser (JCEF), but this IDE is running "
                            + "on a JDK that has no JCEF — it ships only with the JetBrains Runtime. "
                            + "To fix this, open Help ▸ Find Action, run \"Choose Boot Java "
                            + "Runtime for the IDE…\", pick a JetBrains Runtime, and restart the IDE.");
        }
        if (!jcefEnabledInRegistry) {
            return new Diagnosis(Reason.REGISTRY_DISABLED,
                    "The Layout Preview needs the embedded browser (JCEF), but it is turned off in "
                            + "this IDE. To re-enable it, open Help ▸ Find Action, run "
                            + "\"Registry…\", set " + JCEF_REGISTRY_KEY + " to true, and restart the IDE.");
        }
        return new Diagnosis(Reason.INCOMPATIBLE,
                "The Layout Preview needs the embedded browser (JCEF), but it isn't available in "
                        + "this IDE runtime — the bundled JCEF build may be incompatible with this IDE.");
    }

    /** Live probe: reads the registry toggle and the JVM vendor, then maps to a {@link Diagnosis}. */
    static Diagnosis diagnose() {
        return diagnose(isJcefEnabledInRegistry(), System.getProperty("java.vendor"));
    }

    private static boolean isJetBrainsRuntime(String javaVendor) {
        return javaVendor != null && javaVendor.toLowerCase(Locale.ROOT).contains("jetbrains");
    }

    private static boolean isJcefEnabledInRegistry() {
        try {
            return Registry.is(JCEF_REGISTRY_KEY);
        } catch (Exception e) {
            // Key not registered / registry unavailable: assume enabled so we never falsely blame
            // the registry — the diagnosis then falls through to the runtime/incompatible reasons.
            return true;
        }
    }
}
