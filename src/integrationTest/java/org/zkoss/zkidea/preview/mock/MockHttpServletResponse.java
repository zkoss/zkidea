package org.zkoss.zkidea.preview.mock;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

/**
 * Captures the response written by {@code DHtmlLayoutServlet}.
 * Supports both {@link #getWriter()} and {@link #getOutputStream()} so ZK
 * can use whichever path it chooses (controlled by the {@code compress=false}
 * init param which steers it toward {@code getWriter()}).
 */
public class MockHttpServletResponse implements HttpServletResponse {

    private final StringWriter stringWriter = new StringWriter();
    private final PrintWriter printWriter = new PrintWriter(stringWriter, true);
    private final MockServletOutputStream outputStream = new MockServletOutputStream();

    private int status = 200;
    private String contentType;
    private String characterEncoding = "UTF-8";

    // ─── Writer / OutputStream ───────────────────────────────────────────

    @Override
    public PrintWriter getWriter() throws IOException {
        return printWriter;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return outputStream;
    }

    /**
     * Returns the captured HTML. Checks the {@code PrintWriter} path first
     * (used when {@code compress=false}), then falls back to the
     * {@code OutputStream} path.
     */
    public String getContent() {
        printWriter.flush();
        String fromWriter = stringWriter.toString();
        if (!fromWriter.isEmpty()) return fromWriter;

        byte[] bytes = outputStream.getBuffer().toByteArray();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // ─── Status ──────────────────────────────────────────────────────────

    @Override public void setStatus(int sc) { this.status = sc; }
    @Override @SuppressWarnings("deprecation") public void setStatus(int sc, String sm) { this.status = sc; }
    @Override public int getStatus() { return status; }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        this.status = sc;
    }

    @Override
    public void sendError(int sc) throws IOException {
        this.status = sc;
    }

    // ─── Content type / encoding ─────────────────────────────────────────

    @Override public void setContentType(String type) { this.contentType = type; }
    @Override public String getContentType() { return contentType; }
    @Override public void setCharacterEncoding(String charset) { this.characterEncoding = charset; }
    @Override public String getCharacterEncoding() { return characterEncoding; }

    // ─── Headers (stored but not used) ───────────────────────────────────

    @Override public void setHeader(String name, String value) {}
    @Override public void addHeader(String name, String value) {}
    @Override public void setIntHeader(String name, int value) {}
    @Override public void addIntHeader(String name, int value) {}
    @Override public void setDateHeader(String name, long date) {}
    @Override public void addDateHeader(String name, long date) {}
    @Override public boolean containsHeader(String name) { return false; }
    @Override public Collection<String> getHeaderNames() { return Collections.emptyList(); }
    @Override public Collection<String> getHeaders(String name) { return Collections.emptyList(); }
    @Override public String getHeader(String name) { return null; }

    // ─── Content length / locale ─────────────────────────────────────────

    @Override public void setContentLength(int len) {}
    @Override public void setContentLengthLong(long len) {}
    @Override public void setLocale(Locale loc) {}
    @Override public Locale getLocale() { return Locale.getDefault(); }

    // ─── Buffer management ───────────────────────────────────────────────

    @Override public void flushBuffer() throws IOException { printWriter.flush(); }
    @Override public void resetBuffer() {}
    @Override public void reset() {}
    @Override public int getBufferSize() { return 8192; }
    @Override public void setBufferSize(int size) {}
    @Override public boolean isCommitted() { return false; }

    // ─── Redirect / cookies ──────────────────────────────────────────────

    @Override
    public void sendRedirect(String location) throws IOException {
        this.status = 302;
        setHeader("Location", location);
    }

    @Override public void addCookie(Cookie cookie) {}

    @Override public String encodeURL(String url) { return url; }
    @Override public String encodeRedirectURL(String url) { return url; }
    @Override @SuppressWarnings("deprecation") public String encodeUrl(String url) { return url; }
    @Override @SuppressWarnings("deprecation") public String encodeRedirectUrl(String url) { return url; }
}
