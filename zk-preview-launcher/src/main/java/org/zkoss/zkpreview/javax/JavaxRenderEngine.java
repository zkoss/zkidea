package org.zkoss.zkpreview.javax;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import org.zkoss.zkpreview.*;
import org.zkoss.zkpreview.javax.mock.*;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Drives {@code DHtmlLayoutServlet} (page render) and {@code DHtmlUpdateServlet}
 * (resource serving under {@code /zkau/web/*}) against a javax.servlet-flavoured
 * ZK classpath, entirely via mock servlet objects and reflection (extends the
 * proven approach in the uncommitted spike at {@code src/integrationTest/.../ZkMockRenderer.java}).
 */
public class JavaxRenderEngine implements RenderEngine {

    private final ScopedZkClassLoader zkLoader;
    private final MockServletContext servletContext;
    private final MockHttpSession session;
    private final Object layoutServlet;
    private final Method layoutServiceMethod;
    private final Object updateServlet;
    private final Method updateServiceMethod;

    public JavaxRenderEngine(List<File> zkJars, Path webappDir, ForbiddenLoadTracker forbiddenLoadTracker) {
        this.zkLoader = IsolatedRuntime.buildZkClassLoader(zkJars, JavaxRenderEngine.class.getClassLoader(),
                forbiddenLoadTracker);
        this.servletContext = new MockServletContext(webappDir);
        this.session = new MockHttpSession(servletContext);

        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(zkLoader);
        try {
            Class<?> listenerCls = zkLoader.loadClass("org.zkoss.zk.ui.http.HttpSessionListener");
            Object listener = listenerCls.getConstructor().newInstance();
            listenerCls.getMethod("contextInitialized", ServletContextEvent.class)
                    .invoke(listener, new ServletContextEvent(servletContext));

            Class<?> layoutCls = zkLoader.loadClass("org.zkoss.zk.ui.http.DHtmlLayoutServlet");
            layoutServlet = layoutCls.getConstructor().newInstance();
            MockServletConfig layoutConfig = new MockServletConfig("zkLoader", servletContext,
                    Map.of("update-uri", "/zkau", "compress", "false"));
            layoutCls.getMethod("init", ServletConfig.class).invoke(layoutServlet, layoutConfig);
            layoutServiceMethod = layoutCls.getMethod("service", ServletRequest.class, ServletResponse.class);

            Class<?> updateCls = zkLoader.loadClass("org.zkoss.zk.au.http.DHtmlUpdateServlet");
            updateServlet = updateCls.getConstructor().newInstance();
            MockServletConfig updateConfig = new MockServletConfig("auEngine", servletContext, Map.of());
            updateCls.getMethod("init", ServletConfig.class).invoke(updateServlet, updateConfig);
            updateServiceMethod = updateCls.getMethod("service", ServletRequest.class, ServletResponse.class);
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
            MockHttpServletRequest req = new MockHttpServletRequest(session, zulPath);
            MockHttpServletResponse resp = new MockHttpServletResponse();
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
            MockHttpServletRequest req = new MockHttpServletRequest(session, "/zkau", pathInfo, "GET");
            MockHttpServletResponse resp = new MockHttpServletResponse();
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
}
