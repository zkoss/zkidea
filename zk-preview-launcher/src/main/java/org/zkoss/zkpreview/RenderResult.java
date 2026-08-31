package org.zkoss.zkpreview;

/**
 * Outcome of rendering one ZUL page: the rendered HTML, a structured error, or -- when there was
 * no page to render at all -- the HTTP status ZK answered with. Plus which controller mode
 * produced it.
 *
 * <p>The controller fields default to {@link ControllerOutcome#SKIPPED} (isolated) and are set
 * by {@link #withControllers} on the way out of the engine, because this object is the only thing
 * that already travels from the engine to the HTTP layer that reports them. {@link #toJson()} is
 * deliberately unchanged for the first two states -- a {@code controllers} field there is P2-5's
 * job, and {@code StructuredFailureTest} asserts on today's shape.
 *
 * <p><b>Three states, not two.</b> {@link #notServed} exists because a page that is not there is
 * neither a render nor a render failure, and conflating it with either loses the reader's answer:
 * as a success it became an empty {@code 200} that no caller could tell from a page that rendered
 * blank (zkoss/zkidea#71), and as a {@link RenderError} failure it would arrive as a {@code 500}
 * error page with a phase, a message and a stack trace invented for a file that does not exist.
 * Consumers must therefore test {@link #isNotServed()} <em>before</em> reading {@link #getError()},
 * which is null in this state.
 */
public final class RenderResult {
    private final boolean success;
    private final String html;
    private final RenderError error;
    /** The status ZK answered with when no page was served; {@code 0} in the other two states. */
    private final int notServedStatus;
    /** One line naming what was not served and why, or null when a page was served. */
    private final String notServedReason;
    private final ControllerOutcome controllers;
    /** One line naming the controller failure (exception class + first cause message), or null. */
    private final String controllerFailure;

    private RenderResult(boolean success, String html, RenderError error,
            int notServedStatus, String notServedReason,
            ControllerOutcome controllers, String controllerFailure) {
        this.success = success;
        this.html = html;
        this.error = error;
        this.notServedStatus = notServedStatus;
        this.notServedReason = notServedReason;
        this.controllers = controllers;
        this.controllerFailure = controllerFailure;
    }

    public static RenderResult success(String html) {
        return new RenderResult(true, html, null, 0, null, ControllerOutcome.SKIPPED, null);
    }

    public static RenderResult failure(RenderError error) {
        return new RenderResult(false, null, error, 0, null, ControllerOutcome.SKIPPED, null);
    }

    /**
     * No page was served: ZK's renderer answered {@code status} instead of producing HTML.
     *
     * @param status the error status ZK set on the response, {@code >= 400}
     * @param reason one line for the log and the response body; must not be empty
     */
    public static RenderResult notServed(int status, String reason) {
        if (status < 400) {
            throw new IllegalArgumentException("notServed status must be an error status, got " + status);
        }
        if (reason == null || reason.isEmpty()) {
            throw new IllegalArgumentException("reason must not be empty");
        }
        return new RenderResult(false, null, null, status, reason, ControllerOutcome.SKIPPED, null);
    }

    /** Copy of this result carrying the controller outcome of the render that produced it. */
    public RenderResult withControllers(ControllerOutcome outcome, String failure) {
        return new RenderResult(success, html, error, notServedStatus, notServedReason,
                outcome == null ? ControllerOutcome.SKIPPED : outcome, failure);
    }

    public boolean isSuccess() {
        return success;
    }

    /** True when there was no page to render; {@link #getError()} is null in this state. */
    public boolean isNotServed() {
        return notServedStatus != 0;
    }

    public String getHtml() {
        return html;
    }

    public RenderError getError() {
        return error;
    }

    /** The status to answer with when {@link #isNotServed()}; {@code 0} otherwise. */
    public int getNotServedStatus() {
        return notServedStatus;
    }

    /** One line naming what was not served, or null when a page was served. */
    public String getNotServedReason() {
        return notServedReason;
    }

    public ControllerOutcome getControllers() {
        return controllers;
    }

    public String getControllerFailure() {
        return controllerFailure;
    }

    public String toJson() {
        if (success) {
            return "{\"status\":\"SUCCESS\"}";
        }
        if (isNotServed()) {
            // Status only. The reason is request-derived text, and it already reaches the reader on
            // the response body and on stderr -- carrying it here would buy nothing and oblige this
            // class to escape it.
            return "{\"status\":\"NOT_SERVED\",\"httpStatus\":" + notServedStatus + "}";
        }
        return "{\"status\":\"FAILURE\",\"error\":" + error.toJson() + "}";
    }

}
