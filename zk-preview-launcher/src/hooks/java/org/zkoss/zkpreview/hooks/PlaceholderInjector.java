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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Renders MVVM data-binding expressions as visible, dimmed placeholder text so a
 * preview of a data-bound page reads like a wireframe instead of a blank one --
 * mitigation M-1 (doc/zul_preview_product_positioning.md). Complements the isolation
 * seam in {@link PreviewUiFactory} WITHOUT weakening it: it reads only the compile-time
 * annotation *text* parsed from the ZUL and never loads a user class.
 *
 * <p>Two placeholder kinds, both driven off a component's binding annotations:
 * <ul>
 *   <li><b>Text bindings</b> ({@code @load/@save/@bind} on a String display property like
 *       {@code value}/{@code label}/{@code title}): the bound expression (e.g.
 *       {@code vm.name}) is written into that property, dimmed.</li>
 *   <li><b>Model bindings</b> ({@code model="@load(...)"} on a data component such as
 *       {@code grid}/{@code listbox}/{@code combobox}): a small synthetic
 *       {@code ListModelList} of placeholder rows is fed to {@code setModel}, so ZK
 *       renders the component's own {@code <template name="model">} server-side and the
 *       per-cell text bindings above then fill each cell.</li>
 * </ul>
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

    /** Binding kinds that supply a collection to a {@code model} attribute. */
    private static final List<String> MODEL_BINDINGS = Arrays.asList("load", "bind", "save", "init");

    private static final String DIM_STYLE = "color:#9aa0a6;font-style:italic";

    private static final int PLACEHOLDER_ROW_COUNT = 3;

    /** Marks a component whose model we already synthesized, so overlapping composer
     * subtrees (nested viewModels) don't double-inject. */
    private static final String SYNTH_MODEL_FLAG = "zkpreview.syntheticModel";

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
            // Model bindings are injected post-composition (see injectModels): calling
            // setModel here -- before the data component's explicit <rows>/<listhead>
            // child has composed -- makes ZK auto-create a second rows/listhead
            // ("Only one rows child is allowed").
            if ("model".equals(prop)) {
                continue;
            }
            String expr = bindingExpression(ctrl, prop, DISPLAY_BINDINGS);
            if (expr != null && applyPlaceholder(comp, prop, expr)) {
                dim(comp);
            }
        }
    }

    /**
     * Post-composition model injection, invoked from {@link PreviewComposer#doAfterCompose}
     * once the applied subtree (including any explicit {@code <rows>}/{@code <listhead>})
     * is fully built -- the same point at which the real MVVM binder would set the model.
     * Feeds every model-bound data component under {@code root} a synthetic
     * {@code ListModelList} so ZK renders its {@code <template name="model">}; the per-cell
     * text bindings are then filled by {@link #afterComponentAttached}.
     */
    public static void injectModels(Component root) {
        if (root == null || !isolationEnabled()) {
            return;
        }
        List<Component> targets = new ArrayList<>();
        collectModelBound(root, targets);
        for (Component comp : targets) {
            String expr = bindingExpression((ComponentCtrl) comp, "model", MODEL_BINDINGS);
            if (expr != null) {
                injectPlaceholderModel(comp, expr);
            }
        }
    }

    private static void collectModelBound(Component comp, List<Component> out) {
        if (comp instanceof ComponentCtrl) {
            List<String> props = ((ComponentCtrl) comp).getAnnotatedProperties();
            if (props != null && props.contains("model") && comp.getAttribute(SYNTH_MODEL_FLAG) == null) {
                out.add(comp);
            }
        }
        for (Component child : comp.getChildren()) {
            collectModelBound(child, out);
        }
    }

    private static String bindingExpression(ComponentCtrl ctrl, String prop, List<String> bindingNames) {
        Collection<Annotation> anns = ctrl.getAnnotations(prop);
        if (anns == null) {
            return null;
        }
        for (Annotation ann : anns) {
            if (bindingNames.contains(ann.getName())) {
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
     * (e.g. checked) is the scoping guard -- skip silently, never throw. */
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

    /**
     * Feeds a data component ({@code <grid|listbox|combobox ... model="@load(...)">}) a
     * synthetic {@code ListModelList} of N placeholder rows so ZK renders the component's
     * own {@code <template name="model">} server-side (or its default per-item rendering
     * when there is no template); the per-cell {@code @load(each.*)} bindings are then
     * filled by {@link #applyPlaceholder}. Reflection only (no zul compile dep); the rows
     * are our own Strings, so no user class is loaded. Components exposing only a
     * non-{@code ListModel} setter (e.g. {@code Tree}'s {@code TreeModel}) are skipped.
     * Best-effort: any failure leaves the component empty, exactly as before.
     */
    private static void injectPlaceholderModel(Component comp, String expr) {
        try {
            ClassLoader cl = comp.getClass().getClassLoader();
            Class<?> listModelIface = cl.loadClass("org.zkoss.zul.ListModel");
            Class<?> listModelListCls = cl.loadClass("org.zkoss.zul.ListModelList");
            Method setModel;
            try {
                setModel = comp.getClass().getMethod("setModel", listModelIface);
            } catch (NoSuchMethodException noListModel) {
                return; // e.g. Tree (TreeModel only) or a non-data component
            }
            List<String> rows = new ArrayList<>(PLACEHOLDER_ROW_COUNT);
            for (int i = 0; i < PLACEHOLDER_ROW_COUNT; i++) {
                rows.add(expr + "[" + i + "]");
            }
            Object model = listModelListCls.getConstructor(Collection.class).newInstance(rows);
            setModel.invoke(comp, model);
            comp.setAttribute(SYNTH_MODEL_FLAG, Boolean.TRUE);
        } catch (ReflectiveOperationException | RuntimeException ignore) {
            // best-effort only
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
