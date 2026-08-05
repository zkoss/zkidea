package org.zkoss.zkpreview.mockcore;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Package-agnostic core of the mock {@code HttpServletResponse} (review M1, Bridge pattern). Owns the
 * captured body (both the {@link #getWriter()} and {@link #byteBuffer()} paths), status, headers and
 * content-type; the jakarta/javax adapters add only {@code getOutputStream()} (a servlet
 * {@code ServletOutputStream} writing into {@link #byteBuffer()}) and the {@code addCookie} no-op.
 *
 * <p>Supports both {@link #getWriter()} (used for the HTML page render, {@code compress=false}) and
 * the output-stream path (used for binary/text resource bytes served through {@code DHtmlUpdateServlet})
 * so callers can read back whichever path the servlet used.
 */
public class MockHttpServletResponseCore {

    private final StringWriter stringWriter = new StringWriter();
    private final PrintWriter printWriter = new PrintWriter(stringWriter, true);
    private final ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
    private final Map<String, String> headers = new HashMap<>();

    private int status = 200;
    private String contentType;
    private String characterEncoding = "UTF-8";

    public PrintWriter getWriter() {
        return printWriter;
    }

    /** The raw byte sink the adapter's {@code ServletOutputStream} writes into. */
    public ByteArrayOutputStream byteBuffer() {
        return byteBuffer;
    }

    /** Returns the captured body as bytes, preferring the {@code OutputStream} path (used for binary resources). */
    public byte[] getContentBytes() {
        printWriter.flush();
        byte[] fromStream = byteBuffer.toByteArray();
        if (fromStream.length > 0) return fromStream;
        return stringWriter.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Returns the captured body decoded as UTF-8 text (used for the HTML page render). */
    public String getContent() {
        printWriter.flush();
        String fromWriter = stringWriter.toString();
        if (!fromWriter.isEmpty()) return fromWriter;
        return new String(byteBuffer.toByteArray(), StandardCharsets.UTF_8);
    }

    public void setStatus(int sc) {
        this.status = sc;
    }

    public void setStatus(int sc, String sm) {
        this.status = sc;
    }

    public int getStatus() {
        return status;
    }

    public void sendError(int sc, String msg) {
        this.status = sc;
    }

    public void sendError(int sc) {
        this.status = sc;
    }

    public void setContentType(String type) {
        this.contentType = type;
    }

    public String getContentType() {
        return contentType;
    }

    public void setCharacterEncoding(String charset) {
        this.characterEncoding = charset;
    }

    public String getCharacterEncoding() {
        return characterEncoding;
    }

    public void setHeader(String name, String value) {
        headers.put(name.toLowerCase(Locale.ROOT), value);
    }

    public void addHeader(String name, String value) {
        headers.put(name.toLowerCase(Locale.ROOT), value);
    }

    public void setIntHeader(String name, int value) {
        headers.put(name.toLowerCase(Locale.ROOT), String.valueOf(value));
    }

    public void addIntHeader(String name, int value) {
        headers.put(name.toLowerCase(Locale.ROOT), String.valueOf(value));
    }

    public void setDateHeader(String name, long date) {
    }

    public void addDateHeader(String name, long date) {
    }

    public boolean containsHeader(String name) {
        return headers.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public Collection<String> getHeaderNames() {
        return headers.keySet();
    }

    public Collection<String> getHeaders(String name) {
        String v = headers.get(name.toLowerCase(Locale.ROOT));
        return v == null ? Collections.emptyList() : Collections.singletonList(v);
    }

    public String getHeader(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    public void setContentLength(int len) {
    }

    public void setContentLengthLong(long len) {
    }

    public void setLocale(Locale loc) {
    }

    public Locale getLocale() {
        return Locale.getDefault();
    }

    public void flushBuffer() {
        printWriter.flush();
    }

    public void resetBuffer() {
    }

    public void reset() {
    }

    public int getBufferSize() {
        return 8192;
    }

    public void setBufferSize(int size) {
    }

    public boolean isCommitted() {
        return false;
    }

    public void sendRedirect(String location) {
        this.status = 302;
        setHeader("Location", location);
    }

    public String encodeURL(String url) {
        return url;
    }

    public String encodeRedirectURL(String url) {
        return url;
    }

    public String encodeUrl(String url) {
        return url;
    }

    public String encodeRedirectUrl(String url) {
        return url;
    }
}
