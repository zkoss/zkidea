package org.zkoss.zkidea.preview;

/**
 * Outcome of {@link ZulPreviewServerService#preparePreview}: either a ready server
 * (port + request path) or one of the documented non-fatal states the preview panel
 * must explain to the user (R5/R7 in tasks/zul-preview/PLAN.md).
 */
final class PreviewResult {

    enum Status { READY, NO_ZK_JARS, STALE_CLASSPATH, ERROR }

    private final Status status;
    private final int port;
    private final String requestPath;
    private final String message;
    private final String environment;

    private PreviewResult(Status status, int port, String requestPath, String message, String environment) {
        this.status = status;
        this.port = port;
        this.requestPath = requestPath;
        this.message = message;
        this.environment = environment;
    }

    /**
     * A copy carrying the render-target environment block for a GitHub failure report. Set once the
     * preview target resolved, which is where the build tool / docroot layout / ZK jars are known;
     * it stays {@code null} when resolution itself failed, and the reporter then falls back to the
     * plain plugin/IDE/OS/JDK block.
     */
    PreviewResult withEnvironment(String environment) {
        return new PreviewResult(status, port, requestPath, message, environment);
    }

    static PreviewResult ready(int port, String requestPath) {
        return new PreviewResult(Status.READY, port, requestPath, null, null);
    }

    static PreviewResult noZkJars() {
        return new PreviewResult(Status.NO_ZK_JARS, -1, null,
                "No ZK framework jars (zk, zul, ...) were found on this file's module classpath. "
                        + "Add a ZK dependency to the module to enable the Layout Preview.", null);
    }

    /**
     * ZK is declared on the module but its jars aren't on disk (U3): a wiped/unresolved local repo
     * cache. Distinct from {@link #noZkJars()} -- the fix is re-import/re-sync, not "add a dependency".
     */
    static PreviewResult staleClasspath() {
        return new PreviewResult(Status.STALE_CLASSPATH, -1, null,
                "ZK jars are declared on this module but were not found on disk — the local "
                        + "dependency cache looks unresolved or stale. Re-import / re-sync the project "
                        + "(reload the Maven/Gradle project) so the ZK jars resolve, then reopen the "
                        + "Layout Preview.", null);
    }

    static PreviewResult error(String message) {
        return new PreviewResult(Status.ERROR, -1, null,
                "The ZK preview server failed to start.\n\n" + message, null);
    }

    Status getStatus() {
        return status;
    }

    int getPort() {
        return port;
    }

    String getRequestPath() {
        return requestPath;
    }

    String getMessage() {
        return message;
    }

    /** The report environment block, or {@code null} if the target never resolved. */
    String getEnvironment() {
        return environment;
    }
}
