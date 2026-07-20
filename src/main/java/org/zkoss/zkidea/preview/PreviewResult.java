package org.zkoss.zkidea.preview;

/**
 * Outcome of {@link ZulPreviewServerService#preparePreview}: either a ready server
 * (port + request path) or one of the documented non-fatal states the preview panel
 * must explain to the user (R5/R7 in tasks/zul-preview/PLAN.md).
 */
final class PreviewResult {

    enum Status { READY, NO_ZK_JARS, ERROR }

    private final Status status;
    private final int port;
    private final String requestPath;
    private final String message;

    private PreviewResult(Status status, int port, String requestPath, String message) {
        this.status = status;
        this.port = port;
        this.requestPath = requestPath;
        this.message = message;
    }

    static PreviewResult ready(int port, String requestPath) {
        return new PreviewResult(Status.READY, port, requestPath, null);
    }

    static PreviewResult noZkJars() {
        return new PreviewResult(Status.NO_ZK_JARS, -1, null,
                "No ZK framework jars (zk, zul, ...) were found on this file's module classpath. "
                        + "Add a ZK dependency to the module to enable the live preview.");
    }

    static PreviewResult error(String message) {
        return new PreviewResult(Status.ERROR, -1, null,
                "The ZK preview server failed to start.\n\n" + message);
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
}
