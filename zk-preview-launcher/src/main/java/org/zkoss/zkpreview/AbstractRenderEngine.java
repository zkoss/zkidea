package org.zkoss.zkpreview;

import org.zkoss.zkpreview.mockcore.MockHttpServletRequestCore;
import org.zkoss.zkpreview.mockcore.MockHttpServletResponseCore;
import org.zkoss.zkpreview.mockcore.MockServletContextCore;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Servlet-namespace-agnostic core of the render engine (review M1, Template Method). It owns the
 * entire drive logic — classloader isolation, bootstrapping {@code DHtmlLayoutServlet} (page render)
 * and {@code DHtmlUpdateServlet} (resource serving under {@code /zkau/web/*}) by reflection, the
 * render and resource service calls, the AU stub and shutdown — referencing only JDK types and the
 * {@code mockcore} bases. Everything whose type is tied to a servlet namespace — constructing the
 * mock servlet objects, the {@code ServletContextEvent}, and the {@code ServletRequest}/
 * {@code ServletResponse}/{@code ServletConfig}/{@code ServletContextEvent} class literals reflection
 * needs — is a {@code protected} seam the {@code jakarta}/{@code javax} subclasses fill in.
 *
 * <p>Companion to the {@code mockcore} Bridge: the same drift-prone logic that used to be copied per
 * namespace now lives in exactly one place. The subclasses hold no state and only construct their
 * own-namespace mocks, so the base constructor may safely call the seams during bootstrap.
 */
public abstract class AbstractRenderEngine implements RenderEngine {

    private final ScopedZkClassLoader zkLoader;
    /** Shared mock context created by the subclass; downcast back to the adapter type inside the seams. */
    protected final MockServletContextCore servletContext;
    private final Object layoutServlet;
    private final Method layoutServiceMethod;
    private final Object updateServlet;
    private final Method updateServiceMethod;

    protected AbstractRenderEngine(List<File> zkJars, Path webappDir, ForbiddenLoadTracker forbiddenLoadTracker) {
        this.zkLoader = IsolatedRuntime.buildZkClassLoader(zkJars, getClass().getClassLoader(),
                forbiddenLoadTracker);
        this.servletContext = createServletContext(webappDir);

        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(zkLoader);
        try {
            Class<?> listenerCls = zkLoader.loadClass("org.zkoss.zk.ui.http.HttpSessionListener");
            Object listener = listenerCls.getConstructor().newInstance();
            listenerCls.getMethod("contextInitialized", servletContextEventClass())
                    .invoke(listener, newServletContextEvent(servletContext));

            Class<?> layoutCls = zkLoader.loadClass("org.zkoss.zk.ui.http.DHtmlLayoutServlet");
            layoutServlet = layoutCls.getConstructor().newInstance();
            Object layoutConfig = createServletConfig("zkLoader", servletContext,
                    Map.of("update-uri", "/zkau", "compress", "false"));
            layoutCls.getMethod("init", servletConfigClass()).invoke(layoutServlet, layoutConfig);
            layoutServiceMethod = layoutCls.getMethod("service", servletRequestClass(), servletResponseClass());

            Class<?> updateCls = zkLoader.loadClass("org.zkoss.zk.au.http.DHtmlUpdateServlet");
            updateServlet = updateCls.getConstructor().newInstance();
            Object updateConfig = createServletConfig("auEngine", servletContext, Map.of());
            updateCls.getMethod("init", servletConfigClass()).invoke(updateServlet, updateConfig);
            updateServiceMethod = updateCls.getMethod("service", servletRequestClass(), servletResponseClass());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to bootstrap the ZK mock webapp", e);
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    @Override
    public RenderResult renderZul(String zulPath) {
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(zkLoader);
        try {
            MockHttpServletRequestCore req = createRequest(zulPath, null, "GET");
            MockHttpServletResponseCore resp = createResponse();
            layoutServiceMethod.invoke(layoutServlet, req, resp);
            return RenderResult.success(resp.getContent());
        } catch (InvocationTargetException e) {
            return RenderResult.failure(ErrorMapper.map(zulPath, e.getCause() != null ? e.getCause() : e));
        } catch (Exception e) {
            return RenderResult.failure(ErrorMapper.map(zulPath, e));
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    @Override
    public ResourceResult resource(String pathInfo) {
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(zkLoader);
        try {
            MockHttpServletRequestCore req = createRequest("/zkau", pathInfo, "GET");
            MockHttpServletResponseCore resp = createResponse();
            updateServiceMethod.invoke(updateServlet, req, resp);
            int status = resp.getStatus();
            if (status >= 400) return ResourceResult.notFound();
            return ResourceResult.of(status, resp.getContentType(), resp.getContentBytes());
        } catch (Exception e) {
            return ResourceResult.notFound();
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    @Override
    public byte[] auStub() {
        // Valid empty AU response envelope. The preview is a one-shot render with no
        // live desktop, so any interaction (expand a tree node, sort a grid, page a
        // listbox) fires an AU POST we cannot fulfil. The ZK client JSON.parse()es the
        // response (zAu.pushReqCmds), so it must be a JSON object with an empty "rs"
        // command list -- the client then runs zero commands (an inert no-op) instead
        // of showing "Expected JSON format ... Unexpected token '<'". rid:0 is falsy on
        // the client, so the empty command set is applied without a sequence check.
        return "{\"rid\":0,\"rs\":[]}".getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws java.io.IOException {
        zkLoader.close();
    }

    // --- servlet-namespace seams (filled by the jakarta/javax subclasses) ---

    /** Builds the shared mock {@code ServletContext} of this engine's namespace. */
    protected abstract MockServletContextCore createServletContext(Path webappDir);

    /** {@code ServletContextEvent.class} of this namespace, for the {@code contextInitialized} lookup. */
    protected abstract Class<?> servletContextEventClass();

    /** {@code new ServletContextEvent(ctx)} of this namespace. */
    protected abstract Object newServletContextEvent(MockServletContextCore ctx);

    /** {@code ServletConfig.class} of this namespace, for the {@code Servlet.init} lookup. */
    protected abstract Class<?> servletConfigClass();

    /** Builds a mock {@code ServletConfig} of this namespace over the shared context. */
    protected abstract Object createServletConfig(String servletName, MockServletContextCore ctx,
            Map<String, String> initParams);

    /** {@code ServletRequest.class} of this namespace, for the {@code Servlet.service} lookup. */
    protected abstract Class<?> servletRequestClass();

    /** {@code ServletResponse.class} of this namespace, for the {@code Servlet.service} lookup. */
    protected abstract Class<?> servletResponseClass();

    /** Builds a mock request over a fresh session (one session per call — see the subclass {@code newSession}). */
    protected abstract MockHttpServletRequestCore createRequest(String servletPath, String pathInfo, String method);

    /** Builds a mock response of this namespace whose captured body the base reads back. */
    protected abstract MockHttpServletResponseCore createResponse();
}
