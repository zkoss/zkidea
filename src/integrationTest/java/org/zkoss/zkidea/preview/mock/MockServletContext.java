package org.zkoss.zkidea.preview.mock;

import jakarta.servlet.*;
import jakarta.servlet.descriptor.JspConfigDescriptor;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal {@link ServletContext} that maps resource paths to files under a
 * real {@code webappDir}. Only the methods called by ZK during initialisation
 * and ZUL rendering are implemented; all others return safe defaults.
 */
public class MockServletContext implements ServletContext {

    private final Path webappDir;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public MockServletContext(Path webappDir) {
        this.webappDir = webappDir;
    }

    // ─── Resource access ──────────────────────────────────────────────────

    @Override
    public URL getResource(String path) throws MalformedURLException {
        File f = resourceFile(path);
        return f.exists() ? f.toURI().toURL() : null;
    }

    @Override
    public InputStream getResourceAsStream(String path) {
        File f = resourceFile(path);
        if (!f.exists()) return null;
        try {
            return new FileInputStream(f);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public String getRealPath(String path) {
        return resourceFile(path).getAbsolutePath();
    }

    @Override
    public Set<String> getResourcePaths(String path) {
        return null; // ZK treats null as empty — non-fatal
    }

    // ─── Attributes ──────────────────────────────────────────────────────

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(attributes.keySet());
    }

    @Override
    public void setAttribute(String name, Object object) {
        attributes.put(name, object);
    }

    @Override
    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    // ─── Init params ─────────────────────────────────────────────────────

    @Override
    public String getInitParameter(String name) {
        return null;
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return Collections.emptyEnumeration();
    }

    @Override
    public boolean setInitParameter(String name, String value) {
        return false;
    }

    // ─── Server / context info ────────────────────────────────────────────

    @Override
    public String getContextPath() {
        return "";
    }

    @Override
    public String getServerInfo() {
        return "MockServer/1.0";
    }

    @Override
    public int getMajorVersion() {
        return 5;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public int getEffectiveMajorVersion() {
        return 5;
    }

    @Override
    public int getEffectiveMinorVersion() {
        return 0;
    }

    @Override
    public String getMimeType(String file) {
        return null;
    }

    @Override
    public ServletContext getContext(String uripath) {
        return null;
    }

    @Override
    public String getServletContextName() {
        return "MockContext";
    }

    // ─── Logging ─────────────────────────────────────────────────────────

    @Override
    public void log(String msg) {
        System.out.println("[MockCtx] " + msg);
    }

    @Override
    public void log(String message, Throwable throwable) {
        System.out.println("[MockCtx] " + message);
        throwable.printStackTrace(System.out);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void log(Exception exception, String msg) {
        System.out.println("[MockCtx] " + msg);
        exception.printStackTrace(System.out);
    }

    // ─── Deprecated servlet enumeration ──────────────────────────────────

    @Override
    @SuppressWarnings("deprecation")
    public Enumeration<String> getServletNames() { return Collections.emptyEnumeration(); }

    @Override
    @SuppressWarnings("deprecation")
    public Enumeration<Servlet> getServlets() { return Collections.emptyEnumeration(); }

    @Override
    @SuppressWarnings("deprecation")
    public Servlet getServlet(String name) { return null; }

    // ─── ClassLoader ─────────────────────────────────────────────────────

    @Override
    public ClassLoader getClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    // ─── Servlet registration (returns no-op registration to avoid NPE) ──

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, String className) {
        return new NoOpServletRegistration(servletName);
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet) {
        return new NoOpServletRegistration(servletName);
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
        return new NoOpServletRegistration(servletName);
    }

    @Override
    public ServletRegistration.Dynamic addJspFile(String servletName, String jspFile) {
        return new NoOpServletRegistration(servletName);
    }

    @Override
    public <T extends Servlet> T createServlet(Class<T> clazz) { return null; }

    @Override
    public ServletRegistration getServletRegistration(String servletName) { return null; }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations() { return Collections.emptyMap(); }

    // ─── Filter registration (stubs) ─────────────────────────────────────

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, String className) { return null; }

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) { return null; }

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) { return null; }

    @Override
    public <T extends Filter> T createFilter(Class<T> clazz) { return null; }

    @Override
    public FilterRegistration getFilterRegistration(String filterName) { return null; }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations() { return Collections.emptyMap(); }

    // ─── Session config / listeners (stubs) ──────────────────────────────

    @Override
    public SessionCookieConfig getSessionCookieConfig() { return null; }

    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {}

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() { return Collections.emptySet(); }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() { return Collections.emptySet(); }

    @Override
    public void addListener(String className) {}

    @Override
    public <T extends EventListener> void addListener(T t) {}

    @Override
    public void addListener(Class<? extends EventListener> listenerClass) {}

    @Override
    public <T extends EventListener> T createListener(Class<T> clazz) { return null; }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor() { return null; }

    @Override
    public void declareRoles(String... roleNames) {}

    @Override
    public String getVirtualServerName() { return "localhost"; }

    @Override
    public int getSessionTimeout() { return 30; }

    @Override
    public void setSessionTimeout(int sessionTimeout) {}

    @Override
    public String getRequestCharacterEncoding() { return null; }

    @Override
    public void setRequestCharacterEncoding(String encoding) {}

    @Override
    public String getResponseCharacterEncoding() { return null; }

    @Override
    public void setResponseCharacterEncoding(String encoding) {}

    @Override
    public RequestDispatcher getRequestDispatcher(String path) { return null; }

    @Override
    public RequestDispatcher getNamedDispatcher(String name) { return null; }

    // ─── Private helpers ─────────────────────────────────────────────────

    private File resourceFile(String path) {
        String relative = path.startsWith("/") ? path.substring(1) : path;
        return webappDir.resolve(relative).toFile();
    }

    // ─── No-op registration — returned by addServlet/addFilter to prevent NPE ──

    private static class NoOpServletRegistration implements ServletRegistration.Dynamic {
        private final String name;
        NoOpServletRegistration(String name) { this.name = name; }

        @Override public void setAsyncSupported(boolean isAsyncSupported) {}
        @Override public void setLoadOnStartup(int loadOnStartup) {}
        @Override public Set<String> setServletSecurity(ServletSecurityElement constraint) { return Collections.emptySet(); }
        @Override public void setMultipartConfig(MultipartConfigElement multipartConfig) {}
        @Override public void setRunAsRole(String roleName) {}
        @Override public String getName() { return name; }
        @Override public String getClassName() { return null; }
        @Override public boolean setInitParameter(String name, String value) { return false; }
        @Override public String getInitParameter(String name) { return null; }
        @Override public Set<String> setInitParameters(Map<String, String> initParameters) { return Collections.emptySet(); }
        @Override public Map<String, String> getInitParameters() { return Collections.emptyMap(); }
        @Override public Set<String> addMapping(String... urlPatterns) { return Collections.emptySet(); }
        @Override public Collection<String> getMappings() { return Collections.emptyList(); }
        @Override public String getRunAsRole() { return null; }
    }
}
