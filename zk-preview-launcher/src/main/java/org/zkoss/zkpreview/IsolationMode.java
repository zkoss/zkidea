package org.zkoss.zkpreview;

/**
 * Toggle for the isolation hooks (RESEARCH.md U6). On by default; off is the
 * "canary mode" used to prove AC-4's baseline (hook-less renders of fixtures
 * referencing missing classes must fail with a classloading exception).
 */
public final class IsolationMode {

    public static final String SYSTEM_PROPERTY = "zkpreview.isolation";

    private IsolationMode() {
    }

    public static boolean isEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(SYSTEM_PROPERTY));
    }
}
