package org.zkoss.zkpreview;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.zkoss.zkpreview.testutil.Variants;
import org.zkoss.zkpreview.testutil.ZkClasspathResolver;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E1-G1 / AC-3: with isolation hooks ON (default), fixtures (a)-(e) all render
 * SUCCESS, for both servlet-API variants. Markers below were captured once from
 * a real render (see tasks/zul-preview/E1-evidence.md) and hard-coded, per
 * RESEARCH.md U7's own methodology -- not guessed.
 */
class RenderFidelityTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");
    private static final List<String> CANARY_PREFIX = List.of("org.zkoss.zkpreview.testcanary.");

    static Stream<Variants.Named> variants() {
        return Variants.both();
    }

    @ParameterizedTest(name = "(a) plain.zul [{0}]")
    @MethodSource("variants")
    void fixtureA_plainRendersSuccessfully(Variants.Named variant) throws Exception {
        RenderResult r = render(variant, "plain.zul", null);
        assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describeFailure(r));
        String html = r.getHtml();
        assertTrue(html.contains("zul.wnd.Window"), html);
        assertTrue(html.contains("zul.wgt.Label"), html);
        assertTrue(html.contains("Hello ZK"), html);
        assertTrue(html.contains("zul.wgt.Button"), html);
        assertTrue(html.contains("Click me"), html);
    }

    @ParameterizedTest(name = "(b) viewmodel-bind.zul [{0}]")
    @MethodSource("variants")
    void fixtureB_viewModelBindRendersPlaceholderWithoutLoadingUserClass(Variants.Named variant) throws Exception {
        ForbiddenLoadTracker tracker = new ForbiddenLoadTracker(CANARY_PREFIX);
        RenderResult r = render(variant, "viewmodel-bind.zul", tracker);
        assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describeFailure(r));
        String html = r.getHtml();
        assertTrue(html.contains("zul.wnd.Window"), html);
        assertTrue(html.contains("static sibling"), html);
        assertFalse(html.contains("LOADED"), "bound value must not leak: " + html);
        assertFalse(html.contains("CANARY"), "bound value must not leak: " + html);
        assertTrue(html.contains("vm.greeting"),
                () -> "M-1: the @load expression should render as placeholder text: " + html);
        assertTrue(tracker.getAttempts().isEmpty(),
                "the isolation hook must intercept before ZK ever attempts to resolve the ViewModel class, "
                        + "but attempts were recorded: " + tracker.getAttempts());
    }

    @ParameterizedTest(name = "(c) el-missing-var.zul [{0}]")
    @MethodSource("variants")
    void fixtureC_missingElVariableRendersEmpty(Variants.Named variant) throws Exception {
        RenderResult r = render(variant, "el-missing-var.zul", null);
        assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describeFailure(r));
        String html = r.getHtml();
        assertTrue(html.contains("zul.wnd.Window"), html);
        assertTrue(html.contains("static sibling"), html);
        assertFalse(html.contains("missing.prop"),
                "plain EL must NOT be turned into a placeholder (M-1 targets @-annotations only): " + html);
    }

    @ParameterizedTest(name = "(d) missing-composer.zul [{0}]")
    @MethodSource("variants")
    void fixtureD_missingComposerRendersWithNoOpComposer(Variants.Named variant) throws Exception {
        ForbiddenLoadTracker tracker = new ForbiddenLoadTracker(CANARY_PREFIX);
        RenderResult r = render(variant, "missing-composer.zul", tracker);
        assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describeFailure(r));
        String html = r.getHtml();
        assertTrue(html.contains("zul.wnd.Window"), html);
        assertTrue(html.contains("static under composer"), html);
        assertFalse(html.contains("CanaryComposer"), html);
        assertTrue(tracker.getAttempts().isEmpty(),
                "the isolation hook must intercept before ZK ever attempts to resolve the composer class, "
                        + "but attempts were recorded: " + tracker.getAttempts());
    }

    @ParameterizedTest(name = "(e) layout-heavy.zul [{0}]")
    @MethodSource("variants")
    void fixtureE_layoutHeavyRendersAllWidgets(Variants.Named variant) throws Exception {
        RenderResult r = render(variant, "layout-heavy.zul", null);
        assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describeFailure(r));
        String html = r.getHtml();
        for (String marker : new String[]{
                "zul.wnd.Window", "zul.layout.Borderlayout", "zul.layout.North", "zul.layout.Center",
                "zul.layout.South", "zul.tab.Tabbox", "zul.tab.Tabs", "zul.tab.Tab", "zul.tab.Tabpanels",
                "zul.tab.Tabpanel", "zul.grid.Grid", "zul.grid.Columns", "zul.grid.Column", "zul.grid.Rows",
                "zul.grid.Row", "zul.sel.Tree", "zul.sel.Treecols", "zul.sel.Treecol", "zul.sel.Treechildren",
                "zul.sel.Treeitem", "zul.sel.Treerow", "zul.sel.Treecell",
                "North", "South", "Tab 1", "Tab 2", "Name", "Row1", "Node", "Root"}) {
            assertTrue(html.contains(marker), "missing marker '" + marker + "' in: " + html);
        }
    }

    @ParameterizedTest(name = "(f) binding-placeholders.zul [{0}]")
    @MethodSource("variants")
    void fixtureF_bindingExpressionsRenderAsPlaceholders(Variants.Named variant) throws Exception {
        ForbiddenLoadTracker tracker = new ForbiddenLoadTracker(CANARY_PREFIX);
        RenderResult r = render(variant, "binding-placeholders.zul", tracker);
        assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describeFailure(r));
        String html = r.getHtml();
        assertTrue(html.contains("vm.pageTitle"),
                () -> "M-1: the @load title expression should render as placeholder text: " + html);
        assertTrue(html.contains("vm.greeting"),
                () -> "M-1: the @load value expression should render as placeholder text: " + html);
        assertTrue(html.contains("vm.name"),
                () -> "M-1: the @bind value expression should render as placeholder text: " + html);
        assertTrue(html.contains("static"),
                () -> "M-1: static value must still render: " + html);
        assertFalse(html.contains("LOADED"), "real bound value must not leak: " + html);
        assertFalse(html.contains("CANARY"), "real bound value must not leak: " + html);
        assertFalse(html.contains("vm.rows"),
                "a @load on a non-text 'model' property must NOT be injected as text "
                        + "(String-setter scoping guard): " + html);
        assertTrue(tracker.getAttempts().isEmpty(),
                "the isolation hook must intercept before ZK ever attempts to resolve the ViewModel class, "
                        + "but attempts were recorded: " + tracker.getAttempts());
    }

    private static RenderResult render(Variants.Named variant, String fixture, ForbiddenLoadTracker tracker)
            throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);
        try (RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, tracker)) {
            return engine.renderZul("/" + fixture);
        }
    }

    private static String describeFailure(RenderResult r) {
        return r.isSuccess() ? "(success)" : r.getError().toJson();
    }
}
