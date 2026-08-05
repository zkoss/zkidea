package org.zkoss.zkpreview.mockcore;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

/**
 * Package-agnostic core of the mock {@code ServletConfig} (review M1, Bridge pattern): the servlet
 * name and init params. The jakarta/javax adapters add only {@code getServletContext()}.
 */
public class MockServletConfigCore {

    private final String servletName;
    private final Map<String, String> initParams;

    public MockServletConfigCore(String servletName, Map<String, String> initParams) {
        this.servletName = servletName;
        this.initParams = Map.copyOf(initParams);
    }

    public String getServletName() {
        return servletName;
    }

    public String getInitParameter(String name) {
        return initParams.get(name);
    }

    public Enumeration<String> getInitParameterNames() {
        return Collections.enumeration(initParams.keySet());
    }
}
