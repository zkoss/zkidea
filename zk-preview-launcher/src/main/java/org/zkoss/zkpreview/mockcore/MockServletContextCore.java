package org.zkoss.zkpreview.mockcore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Package-agnostic core of the mock {@code ServletContext} (review M1, Bridge pattern): every field
 * and method whose types are servlet-API-independent lives here, so the jakarta and javax adapters
 * ({@code MockServletContext extends MockServletContextCore implements ServletContext}) share this
 * one implementation and only add the servlet-typed stubs.
 *
 * <p>Resolves resources from a real {@code webappDir} (the user's own directory containing the ZUL
 * files). {@code /WEB-INF/zk.xml} is always served from a resource bundled inside this launcher's own
 * jar ({@code /preview/zk.xml}), regardless of whether the user's directory has its own WEB-INF --
 * this is how the isolation-hook registration (ui-factory-class) and other preview-only config reach
 * ZK without requiring the user's project to contain any WEB-INF at all.
 */
public class MockServletContextCore {

    private static final String ZK_XML_PATH = "/WEB-INF/zk.xml";
    private static final String ZK_XML_OVERLAY_RESOURCE = "/preview/zk.xml";

    private final Path webappDir;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public MockServletContextCore(Path webappDir) {
        this.webappDir = webappDir;
    }

    public URL getResource(String path) throws MalformedURLException {
        if (isZkXml(path)) {
            URL overlay = MockServletContextCore.class.getResource(ZK_XML_OVERLAY_RESOURCE);
            if (overlay != null) return overlay;
        }
        File f = resourceFile(path);
        return f != null && f.exists() ? f.toURI().toURL() : null;
    }

    public InputStream getResourceAsStream(String path) {
        if (isZkXml(path)) {
            InputStream overlay = MockServletContextCore.class.getResourceAsStream(ZK_XML_OVERLAY_RESOURCE);
            if (overlay != null) return overlay;
        }
        File f = resourceFile(path);
        if (f == null || !f.exists()) return null;
        try {
            return new FileInputStream(f);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public String getRealPath(String path) {
        File f = resourceFile(path);
        return f == null ? null : f.getAbsolutePath();
    }

    public Set<String> getResourcePaths(String path) {
        return null; // ZK treats null as empty -- non-fatal
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(attributes.keySet());
    }

    public void setAttribute(String name, Object object) {
        attributes.put(name, object);
    }

    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    public String getInitParameter(String name) {
        return null;
    }

    public Enumeration<String> getInitParameterNames() {
        return Collections.emptyEnumeration();
    }

    public boolean setInitParameter(String name, String value) {
        return false;
    }

    public String getContextPath() {
        return "";
    }

    public String getServerInfo() {
        return "ZkPreviewMockServer/1.0";
    }

    public int getMajorVersion() {
        return 5;
    }

    public int getMinorVersion() {
        return 0;
    }

    public int getEffectiveMajorVersion() {
        return 5;
    }

    public int getEffectiveMinorVersion() {
        return 0;
    }

    public String getMimeType(String file) {
        return null;
    }

    public String getServletContextName() {
        return "ZkPreviewMockContext";
    }

    public void log(String msg) {
        System.out.println("[MockCtx] " + msg);
    }

    public void log(String message, Throwable throwable) {
        System.out.println("[MockCtx] " + message);
        throwable.printStackTrace(System.out);
    }

    public void log(Exception exception, String msg) {
        System.out.println("[MockCtx] " + msg);
        exception.printStackTrace(System.out);
    }

    public Enumeration<String> getServletNames() {
        return Collections.emptyEnumeration();
    }

    public ClassLoader getClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    public String getVirtualServerName() {
        return "localhost";
    }

    public int getSessionTimeout() {
        return 30;
    }

    public void setSessionTimeout(int sessionTimeout) {
    }

    public String getRequestCharacterEncoding() {
        return null;
    }

    public void setRequestCharacterEncoding(String encoding) {
    }

    public String getResponseCharacterEncoding() {
        return null;
    }

    public void setResponseCharacterEncoding(String encoding) {
    }

    // Listener/role registration is a no-op in the preview mock; these signatures reference only
    // java.util.EventListener/String, so they live in the servlet-agnostic core.
    public void addListener(String className) {
    }

    public <T extends EventListener> void addListener(T t) {
    }

    public void addListener(Class<? extends EventListener> listenerClass) {
    }

    public <T extends EventListener> T createListener(Class<T> clazz) {
        return null;
    }

    public void declareRoles(String... roleNames) {
    }

    private File resourceFile(String path) {
        String relative = path.startsWith("/") ? path.substring(1) : path;
        Path root = webappDir.normalize();
        Path resolved = root.resolve(relative).normalize();
        // Containment guard (mirrors PreviewHttpServer.readZulSource): a request that escapes the
        // docroot via ../ must not resolve to a file outside it. Returning null is fine -- the
        // servlet spec permits a null translation and every caller here treats null as "not found".
        if (!resolved.startsWith(root)) {
            return null;
        }
        return resolved.toFile();
    }

    private static boolean isZkXml(String path) {
        return ZK_XML_PATH.equals(path) || (path != null && path.equals(ZK_XML_PATH.substring(1)));
    }
}
