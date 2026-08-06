package org.zkoss.zkpreview;

/**
 * Structured render failure. {@code message} must contain the offending fully-qualified
 * class name when a missing class is the cause. {@code zulFile}/{@code line}/{@code column}
 * are best-effort: they are populated only when the failing layer reports a position in its
 * own exception -- guaranteed for BeanShell/{@code <zscript>} failures, and structurally
 * absent for a component-hierarchy {@code UiException} (e.g. "Unsupported parent for row"),
 * whose chain carries no cause and no position-shaped message at all. A null line there is
 * therefore correct, not a mapper defect.
 */
public final class RenderError {
    private final RenderPhase phase;
    private final String message;
    private final String zulFile;
    private final Integer line;
    private final Integer column;
    private final String stackTrace;

    public RenderError(RenderPhase phase, String message, String zulFile, Integer line, Integer column) {
        this(phase, message, zulFile, line, column, null);
    }

    public RenderError(RenderPhase phase, String message, String zulFile, Integer line, Integer column,
                       String stackTrace) {
        if (phase == null) throw new IllegalArgumentException("phase must not be null");
        if (message == null || message.isEmpty()) throw new IllegalArgumentException("message must not be empty");
        this.phase = phase;
        this.message = message;
        this.zulFile = zulFile;
        this.line = line;
        this.column = column;
        this.stackTrace = stackTrace;
    }

    public RenderPhase getPhase() {
        return phase;
    }

    public String getMessage() {
        return message;
    }

    public String getZulFile() {
        return zulFile;
    }

    public Integer getLine() {
        return line;
    }

    public Integer getColumn() {
        return column;
    }

    /** Full stack trace of the causal exception (incl. causes), or {@code null} if unavailable. */
    public String getStackTrace() {
        return stackTrace;
    }

    /** Minimal, dependency-free JSON so the launcher never needs a JSON library. */
    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"phase\":\"").append(phase).append('"');
        sb.append(",\"message\":").append(jsonString(message));
        sb.append(",\"zulFile\":").append(zulFile == null ? "null" : jsonString(zulFile));
        sb.append(",\"line\":").append(line == null ? "null" : line);
        sb.append(",\"column\":").append(column == null ? "null" : column);
        sb.append(",\"stackTrace\":").append(stackTrace == null ? "null" : jsonString(stackTrace));
        sb.append('}');
        return sb.toString();
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
