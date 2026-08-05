package org.zkoss.zkpreview.mockcore;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Package-agnostic core of the mock {@code HttpSession} (review M1, Bridge pattern): the id, timing,
 * the attribute map and the deprecated {@code getValue}/{@code putValue} aliases. The jakarta/javax
 * adapters add only {@code getServletContext()} and {@code getSessionContext()}.
 */
public class MockHttpSessionCore {

    private final String id = UUID.randomUUID().toString();
    private final long creationTime = System.currentTimeMillis();
    private final Map<String, Object> attributes = new HashMap<>();
    private int maxInactiveInterval = 1800;

    public String getId() {
        return id;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getLastAccessedTime() {
        return creationTime;
    }

    public int getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    public void setMaxInactiveInterval(int interval) {
        this.maxInactiveInterval = interval;
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(attributes.keySet());
    }

    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    public void invalidate() {
        attributes.clear();
    }

    public boolean isNew() {
        return true;
    }

    public Object getValue(String name) {
        return attributes.get(name);
    }

    public String[] getValueNames() {
        return attributes.keySet().toArray(new String[0]);
    }

    public void putValue(String name, Object value) {
        attributes.put(name, value);
    }

    public void removeValue(String name) {
        attributes.remove(name);
    }
}
