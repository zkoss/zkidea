package org.zkoss.zkpreview.javax.mock;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.ReadListener;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletContext;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;

import org.zkoss.zkpreview.mockcore.MockHttpServletRequestCore;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;

/**
 * Jakarta {@link HttpServletRequest} adapter over {@link MockHttpServletRequestCore} (review M1,
 * Bridge pattern): the request shape, headers and attributes are inherited from the core; this class
 * supplies only the servlet-typed members -- the {@link MockHttpSession}, cookies, the input stream,
 * async/upgrade, and {@code login}/{@code logout}.
 */
public class MockHttpServletRequest extends MockHttpServletRequestCore implements HttpServletRequest {

    private final MockHttpSession session;

    public MockHttpServletRequest(MockHttpSession session, String servletPath) {
        this(session, servletPath, null, "GET");
    }

    public MockHttpServletRequest(MockHttpSession session, String servletPath, String pathInfo, String method) {
        super(servletPath, pathInfo, method);
        this.session = session;
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
    public String changeSessionId() {
        return session.getId();
    }

    @Override
    public ServletContext getServletContext() {
        return session.getServletContext();
    }

    @Override
    public Cookie[] getCookies() {
        return null;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return new MockServletInputStream(new byte[0]);
    }

    @Override
    public DispatcherType getDispatcherType() {
        return DispatcherType.REQUEST;
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
