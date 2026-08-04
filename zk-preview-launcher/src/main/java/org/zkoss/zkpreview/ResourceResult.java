package org.zkoss.zkpreview;

/** Outcome of a {@code /zkau/web/*} resource request. */
public final class ResourceResult {
    private final boolean found;
    private final int status;
    private final String contentType;
    private final byte[] body;

    private ResourceResult(boolean found, int status, String contentType, byte[] body) {
        this.found = found;
        this.status = status;
        this.contentType = contentType;
        this.body = body;
    }

    public static ResourceResult of(int status, String contentType, byte[] body) {
        return new ResourceResult(true, status, contentType, body);
    }

    public static ResourceResult notFound() {
        return new ResourceResult(false, 404, null, new byte[0]);
    }

    public boolean isFound() {
        return found;
    }

    public int getStatus() {
        return status;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getBody() {
        return body;
    }
}
