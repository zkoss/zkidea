package org.zkoss.zkpreview;

/** Outcome of rendering one ZUL page: either the rendered HTML, or a structured error. */
public final class RenderResult {
    private final boolean success;
    private final String html;
    private final RenderError error;

    private RenderResult(boolean success, String html, RenderError error) {
        this.success = success;
        this.html = html;
        this.error = error;
    }

    public static RenderResult success(String html) {
        return new RenderResult(true, html, null);
    }

    public static RenderResult failure(RenderError error) {
        return new RenderResult(false, null, error);
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

    public String toJson() {
        if (success) {
            return "{\"status\":\"SUCCESS\"}";
        }
        return "{\"status\":\"FAILURE\",\"error\":" + error.toJson() + "}";
    }
}
