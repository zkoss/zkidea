package org.zkoss.zkpreview.jakarta.mock;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionContext;

import org.zkoss.zkpreview.mockcore.MockHttpSessionCore;

/**
 * Jakarta {@link HttpSession} adapter over {@link MockHttpSessionCore} (review M1, Bridge pattern):
 * id, timing and the attribute map are inherited from the core; this class supplies only the
 * servlet-typed {@code getServletContext()} and the deprecated {@code getSessionContext()}.
 */
@SuppressWarnings("deprecation")
public class MockHttpSession extends MockHttpSessionCore implements HttpSession {

    private final MockServletContext servletContext;

    public MockHttpSession(MockServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    @Override
    public HttpSessionContext getSessionContext() {
        return null;
    }
}
