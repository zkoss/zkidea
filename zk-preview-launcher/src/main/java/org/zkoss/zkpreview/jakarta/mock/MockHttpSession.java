package org.zkoss.zkpreview.jakarta.mock;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionContext;

import java.util.*;

/** Map-backed {@link HttpSession} wired to a {@link MockServletContext}. */
@SuppressWarnings("deprecation")
public class MockHttpSession implements HttpSession {

    private final String id = UUID.randomUUID().toString();
    private final long creationTime = System.currentTimeMillis();
    private final MockServletContext servletContext;
    private final Map<String, Object> attributes = new HashMap<>();
    private int maxInactiveInterval = 1800;

    public MockHttpSession(MockServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public long getLastAccessedTime() {
        return creationTime;
    }

    @Override
    public int getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    @Override
    public void setMaxInactiveInterval(int interval) {
        this.maxInactiveInterval = interval;
    }

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(attributes.keySet());
    }

    @Override
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    @Override
    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    @Override
    public void invalidate() {
        attributes.clear();
    }

    @Override
    public boolean isNew() {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public HttpSessionContext getSessionContext() {
        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Object getValue(String name) {
        return attributes.get(name);
    }

    @Override
    @SuppressWarnings("deprecation")
    public String[] getValueNames() {
        return attributes.keySet().toArray(new String[0]);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void putValue(String name, Object value) {
        attributes.put(name, value);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeValue(String name) {
        attributes.remove(name);
    }
}
