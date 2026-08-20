package org.zkoss.zkpreview;

/**
 * Outcome of rendering one ZUL page: either the rendered HTML, or a structured error, plus
 * which controller mode produced it.
 *
 * <p>The controller fields default to {@link ControllerOutcome#SKIPPED} (isolated) and are set
 * by {@link #withControllers} on the way out of the engine, because this object is the only thing
 * that already travels from the engine to the HTTP layer that reports them. {@link #toJson()} is
 * deliberately unchanged -- a {@code controllers} field there is P2-5's job, and
 * {@code StructuredFailureTest} asserts on today's shape.
 */
public final class RenderResult {
    private final boolean success;
    private final String html;
    private final RenderError error;
    private final ControllerOutcome controllers;
    /** One line naming the controller failure (exception class + first cause message), or null. */
    private final String controllerFailure;

    private RenderResult(boolean success, String html, RenderError error,
            ControllerOutcome controllers, String controllerFailure) {
        this.success = success;
        this.html = html;
        this.error = error;
        this.controllers = controllers;
        this.controllerFailure = controllerFailure;
    }

    public static RenderResult success(String html) {
        return new RenderResult(true, html, null, ControllerOutcome.SKIPPED, null);
    }

    public static RenderResult failure(RenderError error) {
        return new RenderResult(false, null, error, ControllerOutcome.SKIPPED, null);
    }

    /** Copy of this result carrying the controller outcome of the render that produced it. */
    public RenderResult withControllers(ControllerOutcome outcome, String failure) {
        return new RenderResult(success, html, error,
                outcome == null ? ControllerOutcome.SKIPPED : outcome, failure);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getHtml() {
        return html;
    }

    public RenderError getError() {
        return error;
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
        return "{\"status\":\"FAILURE\",\"error\":" + error.toJson() + "}";
    }
}
