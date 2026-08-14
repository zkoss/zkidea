package org.zkoss.zkidea.preview;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.jcef.JBCefApp;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Explains <em>why</em> the embedded browser (JCEF) is unavailable and how the user can fix it,
 * so the Layout Preview can give targeted guidance instead of one generic "JCEF not supported"
 * message. Every way JCEF can be missing must map to a reason the user can act on (issue #66).
 *
 * <p>Three things a user can actually change decide it: whether the JCEF classes are reachable at
 * all (since IntelliJ 2026.2 they belong to the bundled plugin {@code com.intellij.modules.jcef},
 * which can be disabled), the IDE registry toggle {@code ide.browser.jcef.enabled}, and whether the
 * IDE's boot runtime is a JetBrains Runtime — up to 2025.2 JCEF shipped inside the JBR, so a boot
 * JDK pointed at a third-party runtime (Oracle, Temurin, Corretto, …) has no JCEF at all.
 *
 * <p>{@link #diagnose(boolean, boolean, String)} is pure (it takes those three signals as
 * arguments) so the reason mapping is unit-tested headlessly; {@link #probe()} is the thin live
 * probe that reads them. The resulting card + external-browser link is JCEF/Swing behaviour with no
 * headless seam; it is verified manually in {@code ./gradlew runIde}, forcing the unavailable path
 * with {@code -Dide.browser.jcef.enabled=false} (screenshot of the verified card:
 * {@code doc/jcef-unavailable.png}).
 *
 * <p>There is no portable "why is JCEF unsupported" platform API to use instead: {@code JBCefApp}
 * does not ship in the platform jars this plugin compiles against. Probing the environment is the
 * deliberate choice.
 */
final class JcefAvailability {

    /** The IDE registry key that turns the embedded browser on/off. */
    static final String JCEF_REGISTRY_KEY = "ide.browser.jcef.enabled";

    enum Reason {
        /** The boot runtime is not a JetBrains Runtime, so no JCEF is bundled. */
        BOOT_JDK_NO_JCEF,
        /** The JCEF classes are unreachable: on 2026.2+ its bundled plugin is disabled or absent. */
        JCEF_PLUGIN_UNAVAILABLE,
        /** JCEF is present but switched off via the registry. */
        REGISTRY_DISABLED,
        /** JCEF is present and enabled, but the browser itself failed to start. */
        INITIALIZATION_FAILED,
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
     * Pure reason mapping from the three probe signals. The boot-JDK check comes first: on a
     * non-JBR runtime, neither enabling a plugin nor flipping the registry would add JCEF, so the
     * runtime is the root cause and the guidance must point there rather than sending the user to
     * the registry.
     */
    static Diagnosis diagnose(boolean jcefClassesLoadable, boolean jcefEnabledInRegistry, String javaVendor) {
        if (!isJetBrainsRuntime(javaVendor)) {
            return new Diagnosis(Reason.BOOT_JDK_NO_JCEF,
                    "The Layout Preview needs the embedded browser (JCEF), but this IDE is running "
                            + "on a JDK that has no JCEF — it ships only with the JetBrains Runtime. "
                            + "To fix this, open Help ▸ Find Action, run \"Choose Boot Java "
                            + "Runtime for the IDE…\", pick a JetBrains Runtime, and restart the IDE.");
        }
        if (!jcefClassesLoadable) {
            return new Diagnosis(Reason.JCEF_PLUGIN_UNAVAILABLE,
                    "The Layout Preview needs the embedded browser (JCEF), but its classes are not "
                            + "available to this plugin. Since IntelliJ 2026.2 JCEF is no longer part "
                            + "of the IDE runtime — it ships as the bundled plugin \"Web Browser "
                            + "(JCEF)\". To fix this, open Settings ▸ Plugins ▸ Installed, enable "
                            + "\"Web Browser (JCEF)\", and restart the IDE.");
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

    /**
     * JCEF is there and enabled, but building the browser threw. Distinct from every
     * {@link #diagnose(boolean, boolean, String)} reason: nothing is missing and there is no setting
     * to change, so the card reports what failed and points at the GitHub report link instead of
     * offering a remedy that would not help.
     */
    static Diagnosis initializationFailed(Throwable failure) {
        return new Diagnosis(Reason.INITIALIZATION_FAILED,
                "The Layout Preview found the embedded browser (JCEF) but could not start it: "
                        + describe(failure) + ". The preview itself is fine — it is only the in-pane "
                        + "browser that failed, so you can still open the render in your system browser.");
    }

    /**
     * Live probe: {@code null} when JCEF is usable, otherwise the reason it is not.
     *
     * <p>{@code JBCefApp.isSupported()} answers "can JCEF run here", but it cannot answer "is JCEF
     * even on this classloader" — asking it when the classes are unreachable throws from the call
     * itself (verified: {@code NoClassDefFoundError: org/cef/handler/CefAppHandler}). Catching that
     * is what turns issue #66's hard failure into a diagnosis.
     *
     * <p>Only {@code NoClassDefFoundError} means "not on the classloader". Catching
     * {@link LinkageError} wholesale would be wrong: {@code ExceptionInInitializerError} is one too,
     * and {@code JBCefApp}'s initializer really can fail for unrelated reasons (observed against the
     * 2026.2 jars), which would have us telling the user to enable a plugin that is already enabled.
     * Anything other than a missing class is JCEF being present but broken, so it is reported as
     * what it is.
     *
     * <p>This method must stay free of any JCEF type in its signature and locals, so that verifying
     * it never triggers a load — see {@link ZulPreviewFileEditor}'s class comment.
     */
    static @Nullable Diagnosis probe() {
        boolean supported;
        try {
            supported = JBCefApp.isSupported();
        } catch (NoClassDefFoundError missing) {
            return diagnose(false, isJcefEnabledInRegistry(), System.getProperty("java.vendor"));
        } catch (Throwable broken) {
            return initializationFailed(broken);
        }
        return supported
                ? null
                : diagnose(true, isJcefEnabledInRegistry(), System.getProperty("java.vendor"));
    }

    private static String describe(Throwable failure) {
        if (failure == null) {
            return "unknown error";
        }
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getName()
                : failure.getClass().getSimpleName() + ": " + message;
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
