package org.zkoss.zkpreview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * {@code setAttribute(name, null)} must behave like {@code removeAttribute(name)}, as the servlet
 * spec mandates and every real container implements. ZK addons rely on it: zkcharts'
 * {@code WebAppInit} stores its (possibly absent, hence null) license code via
 * {@code WebApp.setAttribute}, which {@code SimpleWebApp} forwards straight to the servlet context.
 * The preview mock backed that call with a {@code ConcurrentHashMap}, which rejects nulls, so the
 * NPE escaped through {@code invokeWebAppInits} and killed the whole launcher before it could bind
 * a port -- any project with zkcharts on its classpath could not preview at all.
 */
class MockServletContextAttributeTest {

    @TempDir
    Path tempDir;

    @Test
    void jakartaMockContextTreatsANullAttributeValueAsARemoval() {
        org.zkoss.zkpreview.jakarta.mock.MockServletContext ctx =
                new org.zkoss.zkpreview.jakarta.mock.MockServletContext(tempDir);

        assertDoesNotThrow(() -> ctx.setAttribute("org.zkoss.chart.activeCode", null),
                "a null attribute value must not blow up the webapp bootstrap");
        assertNull(ctx.getAttribute("org.zkoss.chart.activeCode"));
        assertFalse(names(ctx.getAttributeNames()).contains("org.zkoss.chart.activeCode"),
                "a null-valued attribute must not be listed as set");

        // A set-then-null sequence must clear the earlier value, not keep it.
        ctx.setAttribute("k", "v");
        ctx.setAttribute("k", null);
        assertNull(ctx.getAttribute("k"));
        assertFalse(names(ctx.getAttributeNames()).contains("k"));
    }

    @Test
    void javaxMockContextTreatsANullAttributeValueAsARemoval() {
        org.zkoss.zkpreview.javax.mock.MockServletContext ctx =
                new org.zkoss.zkpreview.javax.mock.MockServletContext(tempDir);

        assertDoesNotThrow(() -> ctx.setAttribute("org.zkoss.chart.activeCode", null),
                "a null attribute value must not blow up the webapp bootstrap");
        assertNull(ctx.getAttribute("org.zkoss.chart.activeCode"));
        assertFalse(names(ctx.getAttributeNames()).contains("org.zkoss.chart.activeCode"),
                "a null-valued attribute must not be listed as set");

        ctx.setAttribute("k", "v");
        ctx.setAttribute("k", null);
        assertNull(ctx.getAttribute("k"));
        assertFalse(names(ctx.getAttributeNames()).contains("k"));
    }

    private static List<String> names(java.util.Enumeration<String> e) {
        return Collections.list(e);
    }
}
