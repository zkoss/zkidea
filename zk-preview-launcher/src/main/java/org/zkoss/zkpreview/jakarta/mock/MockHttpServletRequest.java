package org.zkoss.zkpreview.jakarta.mock;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import java.io.*;
import java.security.Principal;
import java.util.*;

/**
 * Minimal {@link HttpServletRequest} covering the three request shapes the
 * launcher dispatches: a ZUL page render ({@code servletPath} = the zul path,
 * {@code pathInfo} = null), and a {@code /zkau/web/*} resource fetch
 * ({@code servletPath} = {@code "/zkau"}, {@code pathInfo} = the remainder).
 */
public class MockHttpServletRequest implements HttpServletRequest {

    private final MockHttpSession session;
    private final String servletPath;
    private final String pathInfo;
    private final String method;
    private final Map<String, Object> attributes = new HashMap<>();
    private final Map<String, String> headers = new HashMap<>();

    public MockHttpServletRequest(MockHttpSession session, String servletPath) {
        this(session, servletPath, null, "GET");
    }

    public MockHttpServletRequest(MockHttpSession session, String servletPath, String pathInfo, String method) {
        this.session = session;
        this.servletPath = servletPath;
        this.pathInfo = pathInfo;
        this.method = method;
    }

    public void setHeader(String name, String value) {
        headers.put(name.toLowerCase(Locale.ROOT), value);
    }

    @Override
    public String getServletPath() {
        return servletPath;
    }

    @Override
    public String getPathInfo() {
        return pathInfo;
    }

    @Override
    public String getRequestURI() {
        return servletPath + (pathInfo == null ? "" : pathInfo);
    }

    @Override
    public StringBuffer getRequestURL() {
        return new StringBuffer("http://localhost:8080" + getRequestURI());
    }

    @Override
    public String getContextPath() {
        return "";
    }

    @Override
    public String getQueryString() {
        return null;
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public String getScheme() {
        return "http";
    }

    @Override
    public String getServerName() {
        return "localhost";
    }

    @Override
    public int getServerPort() {
        return 8080;
    }

    @Override
    public String getLocalAddr() {
        return "127.0.0.1";
    }

    @Override
    public String getLocalName() {
        return "localhost";
    }

    @Override
    public int getLocalPort() {
        return 8080;
    }

    @Override
    public String getRemoteAddr() {
        return "127.0.0.1";
    }

    @Override
    public String getRemoteHost() {
        return "localhost";
    }

    @Override
    public int getRemotePort() {
        return 12345;
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public String getHeader(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String v = getHeader(name);
        return v == null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(v));
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(headers.keySet());
    }

    @Override
    public int getIntHeader(String name) {
        String v = getHeader(name);
        return v == null ? -1 : Integer.parseInt(v);
    }

    @Override
    public long getDateHeader(String name) {
        return -1L;
    }

    @Override
    public HttpSession getSession() {
        return session;
    }

    @Override
    public HttpSession getSession(boolean create) {
        return session;
    }

    @Override
    public String getRequestedSessionId() {
        return session.getId();
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        return true;
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isRequestedSessionIdFromUrl() {
        return false;
    }

    @Override
    public String changeSessionId() {
        return session.getId();
    }

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(attributes.keySet());
    }

    @Override
    public void setAttribute(String name, Object o) {
        attributes.put(name, o);
    }

    @Override
    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    @Override
    public String getParameter(String name) {
        return null;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return Collections.emptyMap();
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.emptyEnumeration();
    }

    @Override
    public String[] getParameterValues(String name) {
        return null;
    }

    @Override
    public Locale getLocale() {
        return Locale.getDefault();
    }

    @Override
    public Enumeration<Locale> getLocales() {
        return Collections.enumeration(Collections.singletonList(Locale.getDefault()));
    }

    @Override
    public String getCharacterEncoding() {
        return null;
    }

    @Override
    public void setCharacterEncoding(String env) {
    }

    @Override
    public int getContentLength() {
        return -1;
    }

    @Override
    public long getContentLengthLong() {
        return -1L;
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return new MockServletInputStream(new byte[0]);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new StringReader(""));
    }

    @Override
    public DispatcherType getDispatcherType() {
        return DispatcherType.REQUEST;
    }

    @Override
    public String getProtocol() {
        return "HTTP/1.1";
    }

    @Override
    public String getAuthType() {
        return null;
    }

    @Override
    public Cookie[] getCookies() {
        return null;
    }

    @Override
    public String getPathTranslated() {
        return null;
    }

    @Override
    public String getRemoteUser() {
        return null;
    }

    @Override
    public boolean isUserInRole(String role) {
        return false;
    }

    @Override
    public Principal getUserPrincipal() {
        return null;
    }

    @Override
    public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
        return false;
    }

    @Override
    public void login(String username, String password) throws ServletException {
    }

    @Override
    public void logout() throws ServletException {
    }

    @Override
    public Collection<Part> getParts() {
        return Collections.emptyList();
    }

    @Override
    public Part getPart(String name) {
        return null;
    }

    @Override
    public boolean isAsyncStarted() {
        return false;
    }

    @Override
    public boolean isAsyncSupported() {
        return false;
    }

    @Override
    public AsyncContext startAsync() {
        throw new UnsupportedOperationException();
    }

    @Override
    public AsyncContext startAsync(ServletRequest req, ServletResponse resp) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AsyncContext getAsyncContext() {
        return null;
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getRealPath(String path) {
        return null;
    }

    @Override
    public ServletContext getServletContext() {
        return session.getServletContext();
    }

    private static class MockServletInputStream extends ServletInputStream {
        private final InputStream delegate;

        MockServletInputStream(byte[] data) {
            this.delegate = new ByteArrayInputStream(data);
        }

        @Override
        public boolean isFinished() {
            try {
                return delegate.available() == 0;
            } catch (IOException e) {
                return true;
            }
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener rl) {
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }
    }
}
