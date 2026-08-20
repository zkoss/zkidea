package org.zkoss.zkpreview;

import java.util.Locale;

/**
 * Process-wide default for the isolation hooks (the no-op {@code UiFactory}/composer chain plus
 * the placeholder injector). On unless {@code -Dzkpreview.isolation=false} says otherwise.
 * {@link Main}'s {@code --isolation on|off} selects the same two modes but not by the same
 * route: the property is the raw hooks-level switch, while the CLI option is carried by
 * {@link ControllerPolicy} and so brings the controller budget and the fail-soft retry with it.
 *
 * <p>Isolation off is a <b>supported mode</b>, not a test-only switch: it is what the
 * {@code zul-writer} skill's {@code --run-controllers} resolves to, and in it the project's real
 * Composer/ViewModel runs and the real {@code Binder} resolves real values. Because that executes
 * arbitrary project code it stays opt-in, and the IntelliJ plugin -- which previews arbitrary
 * pages in arbitrary projects while the user types -- never passes it.
 *
 * <p>The same switch remains the <b>canary</b> the isolation tests use to prove the baseline: with
 * the hooks disabled, a fixture that names a user Composer/ViewModel must fail with a
 * {@code ClassNotFoundException} for that exact FQCN in its cause chain -- which is what makes
 * "the hooks are why nothing loads" a measured claim rather than an assumption.
 *
 * <p>Per-render mode changes do <em>not</em> go through here: see
 * {@code org.zkoss.zkpreview.hooks.IsolationScope} for why that has to be thread-scoped, and
 * {@link ControllerPolicy} for what the launcher carries per render.
 */
public final class IsolationMode {

    public static final String SYSTEM_PROPERTY = "zkpreview.isolation";

    private IsolationMode() {
    }

    public static boolean isEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(SYSTEM_PROPERTY));
    }

    /**
     * Parses the {@code --isolation on|off} CLI value ({@code true}/{@code false} accepted as
     * synonyms). Throws rather than defaulting: a mistyped value that silently meant "isolated"
     * would make a {@code --run-controllers} render look like it ran controllers when it did not,
     * and the reader's judging rules invert on exactly that (P0-2 item 4).
     */
    public static boolean parse(String value) {
        String v = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (v.equals("on") || v.equals("true")) {
            return true;
        }
        if (v.equals("off") || v.equals("false")) {
            return false;
        }
        throw new IllegalArgumentException("Invalid --isolation value '" + value
                + "'. Expected one of: on, off (true/false accepted).");
    }
}
