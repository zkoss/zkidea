package org.zkoss.zkpreview;

/**
 * Toggle for the isolation hooks (the no-op {@code UiFactory}/composer chain). On by
 * default; off is the <b>canary mode</b> the isolation tests use to prove the baseline:
 * with the hooks disabled, a fixture that names a user Composer/ViewModel must fail with
 * a {@code ClassNotFoundException} for that exact FQCN in its cause chain -- which is what
 * makes "the hooks are why nothing loads" a measured claim rather than an assumption.
 * Production never sets it.
 */
public final class IsolationMode {

    public static final String SYSTEM_PROPERTY = "zkpreview.isolation";

    private IsolationMode() {
    }

    public static boolean isEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(SYSTEM_PROPERTY));
    }
}
