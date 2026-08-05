package org.zkoss.zkpreview.javax.mock;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import org.zkoss.zkpreview.mockcore.MockServletConfigCore;

import java.util.Map;

/**
 * Jakarta {@link ServletConfig} adapter over {@link MockServletConfigCore} (review M1, Bridge
 * pattern): the servlet name and init params are inherited from the core; this class supplies only
 * the servlet-typed {@code getServletContext()}.
 */
public class MockServletConfig extends MockServletConfigCore implements ServletConfig {

    private final MockServletContext servletContext;

    public MockServletConfig(String servletName, MockServletContext servletContext, Map<String, String> initParams) {
        super(servletName, initParams);
        this.servletContext = servletContext;
    }

    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }
}
