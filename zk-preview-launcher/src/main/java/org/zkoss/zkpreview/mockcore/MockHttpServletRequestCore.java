package org.zkoss.zkpreview.mockcore;

import java.io.BufferedReader;
import java.io.StringReader;
import java.security.Principal;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Package-agnostic core of the mock {@code HttpServletRequest} (review M1, Bridge pattern). Holds the
 * request shape ({@code servletPath}/{@code pathInfo}/{@code method}), the lowercased header map, the
 * attribute map and every constant getter; the jakarta/javax adapters add only the servlet-typed
 * members (the session, cookies, input stream, async/upgrade, {@code login}/{@code logout}).
 *
 * <p>Covers the three request shapes the launcher dispatches: a ZUL page render ({@code servletPath}
 * = the zul path, {@code pathInfo} = null), and a {@code /zkau/web/*} resource fetch
 * ({@code servletPath} = {@code "/zkau"}, {@code pathInfo} = the remainder).
 */
public class MockHttpServletRequestCore {

    private final String servletPath;
    private final String pathInfo;
    private final String method;
    private final Map<String, Object> attributes = new HashMap<>();
    private final Map<String, String> headers = new HashMap<>();

    public MockHttpServletRequestCore(String servletPath, String pathInfo, String method) {
        this.servletPath = servletPath;
        this.pathInfo = pathInfo;
        this.method = method;
    }

    public void setHeader(String name, String value) {
        headers.put(name.toLowerCase(Locale.ROOT), value);
    }

    public String getServletPath() {
        return servletPath;
    }

    public String getPathInfo() {
        return pathInfo;
    }

    public String getRequestURI() {
        return servletPath + (pathInfo == null ? "" : pathInfo);
    }

    public StringBuffer getRequestURL() {
        return new StringBuffer("http://localhost:8080" + getRequestURI());
    }

    public String getContextPath() {
        return "";
    }

    public String getQueryString() {
        return null;
    }

    public String getMethod() {
        return method;
    }

    public String getScheme() {
        return "http";
    }

    public String getServerName() {
        return "localhost";
    }

    public int getServerPort() {
        return 8080;
    }

    public String getLocalAddr() {
        return "127.0.0.1";
    }

    public String getLocalName() {
        return "localhost";
    }

    public int getLocalPort() {
        return 8080;
    }

    public String getRemoteAddr() {
        return "127.0.0.1";
    }

    public String getRemoteHost() {
        return "localhost";
    }

    public int getRemotePort() {
        return 12345;
    }

    public boolean isSecure() {
        return false;
    }

    public String getHeader(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    public Enumeration<String> getHeaders(String name) {
        String v = getHeader(name);
        return v == null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(v));
    }

    public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(headers.keySet());
    }

    public int getIntHeader(String name) {
        String v = getHeader(name);
        return v == null ? -1 : Integer.parseInt(v);
    }

    public long getDateHeader(String name) {
        return -1L;
    }

    public boolean isRequestedSessionIdValid() {
        return true;
    }

    public boolean isRequestedSessionIdFromCookie() {
        return false;
    }

    public boolean isRequestedSessionIdFromURL() {
        return false;
    }

    public boolean isRequestedSessionIdFromUrl() {
        return false;
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(attributes.keySet());
    }

    public void setAttribute(String name, Object o) {
        attributes.put(name, o);
    }

    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    public String getParameter(String name) {
        return null;
    }

    public Map<String, String[]> getParameterMap() {
        return Collections.emptyMap();
    }

    public Enumeration<String> getParameterNames() {
        return Collections.emptyEnumeration();
    }

    public String[] getParameterValues(String name) {
        return null;
    }

    public Locale getLocale() {
        return Locale.getDefault();
    }

    public Enumeration<Locale> getLocales() {
        return Collections.enumeration(Collections.singletonList(Locale.getDefault()));
    }

    public String getCharacterEncoding() {
        return null;
    }

    public void setCharacterEncoding(String env) {
    }

    public int getContentLength() {
        return -1;
    }

    public long getContentLengthLong() {
        return -1L;
    }

    public String getContentType() {
        return null;
    }

    public BufferedReader getReader() {
        return new BufferedReader(new StringReader(""));
    }

    public String getProtocol() {
        return "HTTP/1.1";
    }

    public String getAuthType() {
        return null;
    }

    public String getPathTranslated() {
        return null;
    }

    public String getRemoteUser() {
        return null;
    }

    public boolean isUserInRole(String role) {
        return false;
    }

    public Principal getUserPrincipal() {
        return null;
    }

    public boolean isAsyncStarted() {
        return false;
    }

    public boolean isAsyncSupported() {
        return false;
    }

    public String getRealPath(String path) {
        return null;
    }
}
