package org.zkoss.zkpreview.javax.mock;

import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.MultipartConfigElement;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletSecurityElement;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;

import org.zkoss.zkpreview.mockcore.MockServletContextCore;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;

/**
 * Jakarta {@link ServletContext} adapter over {@link MockServletContextCore} (review M1, Bridge
 * pattern): the servlet-agnostic behaviour -- resource resolution with the docroot-containment guard,
 * the {@code /WEB-INF/zk.xml} overlay, attributes -- is inherited from the core; this class supplies
 * only the servlet-typed stubs the interface requires.
 */
public class MockServletContext extends MockServletContextCore implements ServletContext {

    public MockServletContext(Path webappDir) {
        super(webappDir);
    }

    @Override
    public ServletContext getContext(String uripath) {
        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Enumeration<Servlet> getServlets() {
        return Collections.emptyEnumeration();
    }

    @Override
    @SuppressWarnings("deprecation")
    public Servlet getServlet(String name) {
        return null;
    }

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
    public <T extends Servlet> T createServlet(Class<T> clazz) {
        return null;
    }

    @Override
    public ServletRegistration getServletRegistration(String servletName) {
        return null;
    }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations() {
        return Collections.emptyMap();
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, String className) {
        return null;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
        return null;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) {
        return null;
    }

    @Override
    public <T extends Filter> T createFilter(Class<T> clazz) {
        return null;
    }

    @Override
    public FilterRegistration getFilterRegistration(String filterName) {
        return null;
    }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
        return Collections.emptyMap();
    }

    @Override
    public SessionCookieConfig getSessionCookieConfig() {
        return null;
    }

    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        return Collections.emptySet();
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        return Collections.emptySet();
    }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        return null;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        return null;
    }

    @Override
    public RequestDispatcher getNamedDispatcher(String name) {
        return null;
    }

    private static class NoOpServletRegistration implements ServletRegistration.Dynamic {
        private final String name;

        NoOpServletRegistration(String name) {
            this.name = name;
        }

        @Override
        public void setAsyncSupported(boolean isAsyncSupported) {
        }

        @Override
        public void setLoadOnStartup(int loadOnStartup) {
        }

        @Override
        public Set<String> setServletSecurity(ServletSecurityElement constraint) {
            return Collections.emptySet();
        }

        @Override
        public void setMultipartConfig(MultipartConfigElement multipartConfig) {
        }

        @Override
        public void setRunAsRole(String roleName) {
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getClassName() {
            return null;
        }

        @Override
        public boolean setInitParameter(String name, String value) {
            return false;
        }

        @Override
        public String getInitParameter(String name) {
            return null;
        }

        @Override
        public Set<String> setInitParameters(Map<String, String> initParameters) {
            return Collections.emptySet();
        }

        @Override
        public Map<String, String> getInitParameters() {
            return Collections.emptyMap();
        }

        @Override
        public Set<String> addMapping(String... urlPatterns) {
            return Collections.emptySet();
        }

        @Override
        public Collection<String> getMappings() {
            return Collections.emptyList();
        }

        @Override
        public String getRunAsRole() {
            return null;
        }
    }
}
