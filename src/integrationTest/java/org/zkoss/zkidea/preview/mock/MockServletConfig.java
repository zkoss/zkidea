package org.zkoss.zkidea.preview.mock;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

/** {@link ServletConfig} for {@code DHtmlLayoutServlet} with essential init params. */
public class MockServletConfig implements ServletConfig {

    private final MockServletContext servletContext;
    private final Map<String, String> initParams;

    public MockServletConfig(MockServletContext servletContext, Map<String, String> initParams) {
        this.servletContext = servletContext;
        this.initParams = Map.copyOf(initParams);
    }

    @Override
    public String getServletName() {
        return "zkLoader";
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
