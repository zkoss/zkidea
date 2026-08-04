package org.zkoss.zkpreview.javax.mock;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Captures the response written by a ZK servlet. Supports both {@link #getWriter()}
 * (used for the HTML page render, {@code compress=false}) and
 * {@link #getOutputStream()} (used for binary/text resource bytes served through
 * {@code DHtmlUpdateServlet}) so callers can read back whichever path the servlet used.
 */
public class MockHttpServletResponse implements HttpServletResponse {

    private final StringWriter stringWriter = new StringWriter();
    private final PrintWriter printWriter = new PrintWriter(stringWriter, true);
    private final MockServletOutputStream outputStream = new MockServletOutputStream();
    private final Map<String, String> headers = new HashMap<>();

    private int status = 200;
    private String contentType;
    private String characterEncoding = "UTF-8";

    @Override
    public PrintWriter getWriter() throws IOException {
        return printWriter;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return outputStream;
    }

    /** Returns the captured body as bytes, preferring the {@code OutputStream} path (used for binary resources). */
    public byte[] getContentBytes() {
        printWriter.flush();
        byte[] fromStream = outputStream.getBuffer().toByteArray();
        if (fromStream.length > 0) return fromStream;
        return stringWriter.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Returns the captured body decoded as UTF-8 text (used for the HTML page render). */
    public String getContent() {
        printWriter.flush();
        String fromWriter = stringWriter.toString();
        if (!fromWriter.isEmpty()) return fromWriter;
        return new String(outputStream.getBuffer().toByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public void setStatus(int sc) {
        this.status = sc;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setStatus(int sc, String sm) {
        this.status = sc;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        this.status = sc;
    }

    @Override
    public void sendError(int sc) throws IOException {
        this.status = sc;
    }

    @Override
    public void setContentType(String type) {
        this.contentType = type;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public void setCharacterEncoding(String charset) {
        this.characterEncoding = charset;
    }

    @Override
    public String getCharacterEncoding() {
        return characterEncoding;
    }

    @Override
    public void setHeader(String name, String value) {
        headers.put(name.toLowerCase(Locale.ROOT), value);
    }

    @Override
    public void addHeader(String name, String value) {
        headers.put(name.toLowerCase(Locale.ROOT), value);
    }

    @Override
    public void setIntHeader(String name, int value) {
        headers.put(name.toLowerCase(Locale.ROOT), String.valueOf(value));
    }

    @Override
    public void addIntHeader(String name, int value) {
        headers.put(name.toLowerCase(Locale.ROOT), String.valueOf(value));
    }

    @Override
    public void setDateHeader(String name, long date) {
    }

    @Override
    public void addDateHeader(String name, long date) {
    }

    @Override
    public boolean containsHeader(String name) {
        return headers.containsKey(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public Collection<String> getHeaderNames() {
        return headers.keySet();
    }

    @Override
    public Collection<String> getHeaders(String name) {
        String v = headers.get(name.toLowerCase(Locale.ROOT));
        return v == null ? Collections.emptyList() : Collections.singletonList(v);
    }

    @Override
    public String getHeader(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public void setContentLength(int len) {
    }

    @Override
    public void setContentLengthLong(long len) {
    }

    @Override
    public void setLocale(Locale loc) {
    }

    @Override
    public Locale getLocale() {
        return Locale.getDefault();
    }

    @Override
    public void flushBuffer() throws IOException {
        printWriter.flush();
    }

    @Override
    public void resetBuffer() {
    }

    @Override
    public void reset() {
    }

    @Override
    public int getBufferSize() {
        return 8192;
    }

    @Override
    public void setBufferSize(int size) {
    }

    @Override
    public boolean isCommitted() {
        return false;
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        this.status = 302;
        setHeader("Location", location);
    }

    @Override
    public void addCookie(Cookie cookie) {
    }

    @Override
    public String encodeURL(String url) {
        return url;
    }

    @Override
    public String encodeRedirectURL(String url) {
        return url;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String encodeUrl(String url) {
        return url;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String encodeRedirectUrl(String url) {
        return url;
    }
}
