package org.zkoss.zkidea.preview;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.zkoss.zkidea.preview.mock.*;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Map;

/**
 * Orchestrates loading ZK via URLClassLoader and calling
 * {@code DHtmlLayoutServlet.service()} with mock servlet objects.
 *
 * <p>ZK 10.x-jakarta uses {@code jakarta.servlet.*} (Jakarta EE 9 / Servlet 5.0).
 * The URLClassLoader's parent is this class's classloader, which has
 * {@code jakarta.servlet-api:5.0.0} on its classpath. Since ZK declares
 * servlet-api as {@code provided} (absent from its JARs), all
 * {@code jakarta.servlet.*} references resolve from the parent via delegation.
 * Mocks implement those same interfaces → no {@code ClassCastException}.
 */
public class ZkMockRenderer {

    /**
     * Renders a ZUL file by calling {@code DHtmlLayoutServlet} directly in-process.
     *
     * @param zkClasspath OS-separated classpath of ZK JARs (from mvn dependency:build-classpath)
     * @param webappDir   directory containing {@code WEB-INF/zk.xml} and the ZUL file
     * @param zulPath     servlet path of the ZUL, e.g. {@code "/test.zul"}
     * @return rendered HTML
     * @throws Exception if any reflection or servlet call fails
     */
    public static String render(String zkClasspath, Path webappDir, String zulPath) throws Exception {
        URL[] urls = classpathToUrls(zkClasspath);
        ClassLoader parent = ZkMockRenderer.class.getClassLoader();
        // Child-first for org.zkoss.* so the 10.1.0-jakarta JARs from the resolved
        // classpath take precedence over any older ZK version transitively present
        // in the test classpath (e.g. zkbind:10.0.0-jakarta from testImplementation).
        URLClassLoader zkLoader = new URLClassLoader(urls, parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve)
                    throws ClassNotFoundException {
                if (name.startsWith("org.zkoss.")) {
                    Class<?> c = findLoadedClass(name);
                    if (c == null) {
                        try { c = findClass(name); } catch (ClassNotFoundException ignored) {}
                    }
                    if (c != null) {
                        if (resolve) resolveClass(c);
                        return c;
                    }
                }
                return super.loadClass(name, resolve);
            }
        };

        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(zkLoader);
        try {
            MockServletContext ctx = new MockServletContext(webappDir);
            MockHttpSession session = new MockHttpSession(ctx);
            MockHttpServletRequest req = new MockHttpServletRequest(session, zulPath);
            MockHttpServletResponse resp = new MockHttpServletResponse();

            // Initialize ZK WebManager via HttpSessionListener.contextInitialized().
            // HttpSessionListener23.contextInitialized takes jakarta.servlet.ServletContextEvent.
            // We locate the method by name to avoid any subtle classloader class-identity
            // edge cases that can occur when both javax and jakarta APIs are on the classpath.
            Class<?> listenerCls = zkLoader.loadClass("org.zkoss.zk.ui.http.HttpSessionListener");
            Object listener = listenerCls.getConstructor().newInstance();
            // contextInitialized is declared in HttpSessionListener23 (superclass of HttpSessionListener).
            // Both classes are loaded by zkLoader whose parent has jakarta.servlet-api:5.0.0,
            // so jakarta.servlet.ServletContextEvent resolves from the shared parent — same class.
            listenerCls.getMethod("contextInitialized", ServletContextEvent.class)
                       .invoke(listener, new ServletContextEvent(ctx));

            // Create DHtmlLayoutServlet and init with config.
            Class<?> servletCls = zkLoader.loadClass("org.zkoss.zk.ui.http.DHtmlLayoutServlet");
            Object servlet = servletCls.getConstructor().newInstance();
            MockServletConfig config = new MockServletConfig(ctx,
                    Map.of("update-uri", "/zkau", "compress", "false"));

            // init(ServletConfig) is inherited from HttpServlet — it stores the config then calls init().
            // DHtmlLayoutServlet also overrides init() (no-arg) with ZK-specific setup.
            // jakarta.servlet.ServletConfig resolves from the parent classloader (shared with zkLoader).
            servletCls.getMethod("init", ServletConfig.class).invoke(servlet, config);

            // HttpServlet.service(HttpServletRequest, HttpServletResponse) is PROTECTED.
            // The PUBLIC overload is GenericServlet.service(ServletRequest, ServletResponse),
            // which dispatches internally to the Http-specific protected version.
            // MockHttpServletRequest/Response implement HttpServletRequest/Response which
            // extends ServletRequest/Response, so the cast inside HttpServlet succeeds.
            servletCls.getMethod("service", ServletRequest.class, ServletResponse.class)
                      .invoke(servlet, req, resp);

            return resp.getContent();

        } finally {
            Thread.currentThread().setContextClassLoader(prev);
            zkLoader.close();
        }
    }

    private static URL[] classpathToUrls(String classpath) throws Exception {
        String[] entries = classpath.split(File.pathSeparator);
        URL[] urls = new URL[entries.length];
        for (int i = 0; i < entries.length; i++) {
            urls[i] = new File(entries[i].trim()).toURI().toURL();
        }
        return urls;
    }
}
