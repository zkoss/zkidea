package org.zkoss.zkpreview.jakarta.mock;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

/** {@link ServletConfig} carrying the init params a ZK servlet needs. */
public class MockServletConfig implements ServletConfig {

    private final String servletName;
    private final MockServletContext servletContext;
    private final Map<String, String> initParams;

    public MockServletConfig(String servletName, MockServletContext servletContext, Map<String, String> initParams) {
        this.servletName = servletName;
        this.servletContext = servletContext;
        this.initParams = Map.copyOf(initParams);
    }

    @Override
    public String getServletName() {
        return servletName;
    }

    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    @Override
    public String getInitParameter(String name) {
        return initParams.get(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return Collections.enumeration(initParams.keySet());
    }
}
