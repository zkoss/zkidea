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
 * <p>Registered via zk.xml {@code <listener>}. Gated by the same {@link IsolationScope} switch
 * as {@link PreviewUiFactory}, at all three of its entry points. With isolation off -- the
 * launcher's supported {@code --isolation off} mode, which {@code preview-zul.py} reaches through
 * {@code --run-controllers} (P0-2), and also the AC-4 canary tests -- the project's real
 * ViewModel/Composer runs and the real {@code Binder} resolves real values, so this injector
 * stands down entirely: no placeholder text, no synthetic model rows, no dim styling. A blank
 * field in that mode is therefore a genuine gap in the page or its controller, not a preview
 * artefact.
 */
public class PlaceholderInjector implements UiLifeCycle {

    /** Value-carrying display bindings (vs id/init/command/converter/validator/ref/template). */
    private static final List<String> DISPLAY_BINDINGS = Arrays.asList("load", "save", "bind");

    /** Binding kinds that supply a collection to a {@code model} attribute. */
    private static final List<String> MODEL_BINDINGS = Arrays.asList("load", "bind", "save", "init");

    /**
     * The one property ZK <em>loads</em> as a URI rather than showing as text
     * ({@code include}/{@code iframe}/{@code image}/{@code audio}). A placeholder is display
     * text, so writing binding text here does not read as a placeholder at all: ZK takes the
     * text for a path. On {@code <include src>} that is a hard failure -- a path not ending in
     * ".zul" is not an instant ZUL include, so ZK routes it through {@code Execution.include}
     * to the mock context's (deliberately) null {@code RequestDispatcher}, leaving a red
     * "No dispatcher available to include ..." box inside the previewed page (issue #69).
     *
     * <p>Deliberately just {@code src}: other URI-ish properties ({@code href} on a link,
     * {@code image} on a button) are resolved by the browser, not by ZK, so a bound one
     * degrades to a dead link or a broken-image icon -- never a failed render.
     */
    private static final String URI_PROPERTY = "src";

    private static final String DIM_STYLE = "color:#9aa0a6;font-style:italic";

    private static final int PLACEHOLDER_ROW_COUNT = 3;

    /** Marks a component whose model we already synthesized, so overlapping composer
     * subtrees (nested viewModels) don't double-inject. */
    private static final String SYNTH_MODEL_FLAG = "zkpreview.syntheticModel";

    /** Marks a component we gave a text placeholder, so the post-composition {@link #dimPlaceholders}
     * sweep can style it AFTER ZK has applied any static {@code style} attribute -- a style set during
     * {@link #afterComponentAttached} is overwritten (or dropped) before the page is serialized. */
    private static final String PLACEHOLDER_FLAG = "zkpreview.placeholder";

    @Override
    public void afterComponentAttached(Component comp, Page page) {
        if (!IsolationScope.isEnabled() || !(comp instanceof ComponentCtrl)) {
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
            if (expr == null) {
                continue;
            }
            if (URI_PROPERTY.equalsIgnoreCase(prop)) {
                applyBoundUri(comp, prop, expr);
                continue;
            }
            if (applyPlaceholder(comp, prop, expr)) {
                // Dimming is applied post-composition (dimPlaceholders), not here: a style set at
                // attach time does not survive to the serialized page. Just mark the component.
                comp.setAttribute(PLACEHOLDER_FLAG, Boolean.TRUE);
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
        if (root == null || !IsolationScope.isEnabled()) {
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

    /**
     * Resolves a bound {@link #URI_PROPERTY} without ever writing expression text into it.
     * A constant string literal ({@code src="@load('~./page.zul')"}) is already the real path,
     * fully known at parse time, so unquote it and let ZK load it for real -- exactly what the
     * binder would do, and no user class is touched. Any other expression reads the ViewModel,
     * which never runs under isolation: leave the property unset, which is also what the real
     * binder produces for an unresolvable value (nothing is included, and the host page renders).
     */
    private static void applyBoundUri(Component comp, String prop, String expr) {
        String path = constantStringLiteral(expr);
        if (path != null) {
            // A resolved path, not a placeholder: no PLACEHOLDER_FLAG, so it is never dimmed.
            applyPlaceholder(comp, prop, path);
        }
    }

    /**
     * The text of a wholly quoted constant string expression, else null (a ViewModel reference,
     * a concatenation, an empty literal). An inner occurrence of the same quote means the
     * expression merely starts and ends with one -- {@code '/pages/'.concat(vm.name)} -- so it
     * is not a constant.
     */
    private static String constantStringLiteral(String expr) {
        if (expr.length() < 3) {
            return null;
        }
        char quote = expr.charAt(0);
        if ((quote != '\'' && quote != '"') || expr.charAt(expr.length() - 1) != quote) {
            return null;
        }
        String inner = expr.substring(1, expr.length() - 1);
        return inner.indexOf(quote) < 0 && !inner.trim().isEmpty() ? inner : null;
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
     * Feeds a data component ({@code <grid|listbox|combobox|tree ... model="@load(...)">})
     * a synthetic model of N placeholder rows/nodes so ZK renders the component's own
     * {@code <template name="model">} server-side (or its default per-item rendering when
     * there is no template); the per-cell {@code @load(each.*)} bindings are then filled by
     * {@link #applyPlaceholder}. A {@code ListModelList} covers grid/listbox/combobox/
     * selectbox; a {@code DefaultTreeModel} covers {@code Tree}. Reflection only (no zul
     * compile dep); rows/nodes are our own Strings, so no user class is loaded.
     * Best-effort: any failure leaves the component empty, exactly as before.
     */
    private static void injectPlaceholderModel(Component comp, String expr) {
        try {
            ClassLoader cl = comp.getClass().getClassLoader();
            Method setModel = findModelSetter(comp, cl, "org.zkoss.zul.ListModel");
            Object model;
            if (setModel != null) {
                model = syntheticListModel(cl, expr);
            } else {
                setModel = findModelSetter(comp, cl, "org.zkoss.zul.TreeModel");
                if (setModel == null) {
                    return; // no supported model setter
                }
                model = syntheticTreeModel(cl, expr);
            }
            setModel.invoke(comp, model);
            comp.setAttribute(SYNTH_MODEL_FLAG, Boolean.TRUE);
        } catch (ReflectiveOperationException | RuntimeException ignore) {
            // best-effort only
        }
    }

    private static Method findModelSetter(Component comp, ClassLoader cl, String modelIface) {
        try {
            return comp.getClass().getMethod("setModel", cl.loadClass(modelIface));
        } catch (ReflectiveOperationException notFound) {
            return null;
        }
    }

    private static Object syntheticListModel(ClassLoader cl, String expr) throws ReflectiveOperationException {
        Class<?> listModelListCls = cl.loadClass("org.zkoss.zul.ListModelList");
        List<String> rows = new ArrayList<>(PLACEHOLDER_ROW_COUNT);
        for (int i = 0; i < PLACEHOLDER_ROW_COUNT; i++) {
            rows.add(expr + "[" + i + "]");
        }
        return listModelListCls.getConstructor(Collection.class).newInstance(rows);
    }

    /** A shallow synthetic tree: root -> N branches, each with 2 leaves. */
    private static Object syntheticTreeModel(ClassLoader cl, String expr) throws ReflectiveOperationException {
        Class<?> nodeCls = cl.loadClass("org.zkoss.zul.DefaultTreeNode");
        Class<?> modelCls = cl.loadClass("org.zkoss.zul.DefaultTreeModel");
        Class<?> treeNodeIface = cl.loadClass("org.zkoss.zul.TreeNode");
        java.lang.reflect.Constructor<?> leaf = nodeCls.getConstructor(Object.class);
        java.lang.reflect.Constructor<?> branch = nodeCls.getConstructor(Object.class, Collection.class);
        List<Object> branches = new ArrayList<>(PLACEHOLDER_ROW_COUNT);
        for (int i = 0; i < PLACEHOLDER_ROW_COUNT; i++) {
            List<Object> leaves = new ArrayList<>(2);
            for (int j = 0; j < 2; j++) {
                leaves.add(leaf.newInstance(expr + "[" + i + "." + j + "]"));
            }
            branches.add(branch.newInstance(expr + "[" + i + "]", leaves));
        }
        Object root = branch.newInstance(expr, branches);
        return modelCls.getConstructor(treeNodeIface).newInstance(root);
    }

    /**
     * Post-composition dim sweep, invoked from {@link PreviewComposer#doAfterCompose} after
     * {@link #injectModels}. Applies the placeholder dim style to every component we gave a text
     * placeholder. Done here rather than in {@link #afterComponentAttached} because a style set at
     * attach time is overwritten when ZK later assigns the component's own static {@code style}
     * attribute (and is otherwise dropped before serialization); by this point all static properties
     * -- and any model-template rows created by {@link #injectModels} -- exist, so the dim sticks.
     */
    public static void dimPlaceholders(Component root) {
        if (root == null || !IsolationScope.isEnabled()) {
            return;
        }
        dimTree(root);
    }

    private static void dimTree(Component comp) {
        if (comp.getAttribute(PLACEHOLDER_FLAG) != null) {
            dim(comp);
            comp.removeAttribute(PLACEHOLDER_FLAG); // dim once, even if composer subtrees overlap
        }
        for (Component child : comp.getChildren()) {
            dimTree(child);
        }
    }

    private static void dim(Component comp) {
        if (comp instanceof HtmlBasedComponent) {
            try {
                HtmlBasedComponent hc = (HtmlBasedComponent) comp;
                String existing = hc.getStyle();
                // Append, don't skip: a component that already carries an author style must still be
                // dimmed (else a styled bound value renders as undimmed literal text). The dim rules
                // go last so they win the cascade for the placeholder look.
                hc.setStyle(existing == null || existing.isEmpty() ? DIM_STYLE : existing + ";" + DIM_STYLE);
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
