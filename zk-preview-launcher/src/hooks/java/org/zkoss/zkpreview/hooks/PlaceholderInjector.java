package org.zkoss.zkpreview.hooks;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.HtmlBasedComponent;
import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.ShadowElement;
import org.zkoss.zk.ui.metainfo.Annotation;
import org.zkoss.zk.ui.sys.ComponentCtrl;
import org.zkoss.zk.ui.util.UiLifeCycle;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Renders MVVM data-binding expressions as visible, dimmed placeholder text so a
 * preview of a data-bound page reads like a wireframe (the field's bound expression,
 * e.g. {@code vm.name}) instead of blank -- mitigation M-1
 * (doc/zul_preview_product_positioning.md). Complements the isolation seam in
 * {@link PreviewUiFactory} WITHOUT weakening it: it reads only the compile-time
 * annotation *text* parsed from the ZUL and never loads a user class.
 *
 * <p>Registered via zk.xml {@code <listener>}. Gated by the same
 * {@code zkpreview.isolation} switch as {@link PreviewUiFactory}: when isolation is
 * off (canary mode) the real Binder runs and resolves real values, so this injector
 * stands down.
 */
public class PlaceholderInjector implements UiLifeCycle {

    private static final String ISOLATION_PROPERTY = "zkpreview.isolation";

    /** Value-carrying display bindings (vs id/init/command/converter/validator/ref/template). */
    private static final List<String> DISPLAY_BINDINGS = Arrays.asList("load", "save", "bind");

    private static final String DIM_STYLE = "color:#9aa0a6;font-style:italic";

    private static boolean isolationEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(ISOLATION_PROPERTY));
    }

    @Override
    public void afterComponentAttached(Component comp, Page page) {
        if (!isolationEnabled() || !(comp instanceof ComponentCtrl)) {
            return;
        }
        ComponentCtrl ctrl = (ComponentCtrl) comp;
        List<String> props = ctrl.getAnnotatedProperties();
        if (props == null) {
            return;
        }
        for (String prop : props) {
            String expr = displayBindingExpression(ctrl, prop);
            if (expr != null && applyPlaceholder(comp, prop, expr)) {
                dim(comp);
            }
        }
    }

    private static String displayBindingExpression(ComponentCtrl ctrl, String prop) {
        Collection<Annotation> anns = ctrl.getAnnotations(prop);
        if (anns == null) {
            return null;
        }
        for (Annotation ann : anns) {
            if (DISPLAY_BINDINGS.contains(ann.getName())) {
                String expr = ann.getAttribute("value");
                if (expr == null && ann.getAttributes() != null) {
                    for (String[] vals : ann.getAttributes().values()) {
                        if (vals != null && vals.length > 0 && vals[0] != null) {
                            expr = vals[0];
                            break;
                        }
                    }
                }
                if (expr != null && !expr.trim().isEmpty()) {
                    return expr.trim();
                }
            }
        }
        return null;
    }

    /** Reflectively invokes set<Prop>(String); true iff set. A missing String setter
     * (e.g. model/checked) is the scoping guard -- skip silently, never throw. */
    private static boolean applyPlaceholder(Component comp, String prop, String expr) {
        if (prop == null || prop.isEmpty()) {
            return false;
        }
        String setter = "set" + Character.toUpperCase(prop.charAt(0)) + prop.substring(1);
        try {
            Method m = comp.getClass().getMethod(setter, String.class);
            m.invoke(comp, expr);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignore) {
            return false;
        }
    }

    private static void dim(Component comp) {
        if (comp instanceof HtmlBasedComponent) {
            try {
                HtmlBasedComponent hc = (HtmlBasedComponent) comp;
                if (hc.getStyle() == null || hc.getStyle().isEmpty()) {
                    hc.setStyle(DIM_STYLE);
                }
            } catch (RuntimeException ignore) {
                // best-effort only
            }
        }
    }

    @Override public void afterComponentDetached(Component comp, Page prevpage) { }
    @Override public void afterComponentMoved(Component parent, Component child, Component ref) { }
    @Override public void afterShadowAttached(ShadowElement shadow, Component host) { }
    @Override public void afterShadowDetached(ShadowElement shadow, Component prevhost) { }
    @Override public void afterPageAttached(Page page, Desktop desktop) { }
    @Override public void afterPageDetached(Page page, Desktop desktop) { }
}
