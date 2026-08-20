package org.zkoss.zkpreview;

/**
 * What happened to the project's controllers (Composers/ViewModels) during one render -- the
 * machine-readable half of P0-2 item 4, carried to the caller on the
 * {@code X-ZK-Preview-Controllers} response header.
 *
 * <p>Tokens are lower-case ASCII so they are valid header values as-is. The human wording
 * ({@code CONTROLLERS: failed -> isolated} and friends) is deliberately NOT here: the text
 * contract belongs to {@code preview-zul.py}, which owns every line it prints.
 */
public enum ControllerOutcome {

    /** Isolated render: the hooks substituted a no-op composer, so no project code ran. */
    SKIPPED("skipped"),

    /** Controllers ran to completion and their render is what is being served. */
    EXECUTED("executed"),

    /** Controllers were attempted, failed or overran the budget, and an isolated retry is
     * what is being served (see {@link RenderResult#getControllerFailure()}). */
    FAILED("failed");

    private final String token;

    ControllerOutcome(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }
}
