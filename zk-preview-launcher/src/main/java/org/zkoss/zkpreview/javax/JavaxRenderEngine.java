package org.zkoss.zkpreview.javax;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.zkoss.zkpreview.AbstractRenderEngine;
import org.zkoss.zkpreview.ForbiddenLoadTracker;
import org.zkoss.zkpreview.javax.mock.MockHttpServletRequest;
import org.zkoss.zkpreview.javax.mock.MockHttpServletResponse;
import org.zkoss.zkpreview.javax.mock.MockHttpSession;
import org.zkoss.zkpreview.javax.mock.MockServletConfig;
import org.zkoss.zkpreview.javax.mock.MockServletContext;
import org.zkoss.zkpreview.mockcore.MockHttpServletRequestCore;
import org.zkoss.zkpreview.mockcore.MockHttpServletResponseCore;
import org.zkoss.zkpreview.mockcore.MockServletContextCore;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * javax.servlet realisation of {@link AbstractRenderEngine} (review M1, Template Method): the base
 * owns the whole render/resource/bootstrap logic; this subclass only constructs javax-flavoured
 * mocks and hands back the javax {@code Servlet*} class literals reflection needs. Kept as a
 * distinct class so {@code RenderEngineFactory} and {@code IsolationTest} can identify the servlet
 * flavour by type. Each per-namespace method is the exact twin of its sibling engine in the other
 * servlet namespace (identical modulo the {@code javax}/{@code javax} token).
 */
public class JavaxRenderEngine extends AbstractRenderEngine {

    public JavaxRenderEngine(List<File> zkJars, Path webappDir, ForbiddenLoadTracker forbiddenLoadTracker) {
        super(zkJars, webappDir, forbiddenLoadTracker);
    }

    @Override
    protected MockServletContextCore createServletContext(Path webappDir) {
        return new MockServletContext(webappDir);
    }

    @Override
    protected Class<?> servletContextEventClass() {
        return ServletContextEvent.class;
    }

    @Override
    protected Object newServletContextEvent(MockServletContextCore ctx) {
        return new ServletContextEvent((MockServletContext) ctx);
    }

    @Override
    protected Class<?> servletConfigClass() {
        return ServletConfig.class;
    }

    @Override
    protected Object createServletConfig(String servletName, MockServletContextCore ctx,
            Map<String, String> initParams) {
        return new MockServletConfig(servletName, (MockServletContext) ctx, initParams);
    }

    @Override
    protected Class<?> servletRequestClass() {
        return ServletRequest.class;
    }

    @Override
    protected Class<?> servletResponseClass() {
        return ServletResponse.class;
    }

    @Override
    protected MockHttpServletRequestCore createRequest(String servletPath, String pathInfo, String method) {
        return new MockHttpServletRequest(newSession(), servletPath, pathInfo, method);
    }

    @Override
    protected MockHttpServletResponseCore createResponse() {
        return new MockHttpServletResponse();
    }

    /**
     * A servlet session for a single render/resource call. L1: each call gets its OWN session
     * so ZK desktops can't accumulate in one long-lived session for the helper JVM's whole life
     * (and so separate preview tabs don't share one session, as they wouldn't on a real server).
     * Package-visible so a test can observe that a fresh one is created per call.
     */
    MockHttpSession newSession() {
        return new MockHttpSession((MockServletContext) servletContext);
    }
}
