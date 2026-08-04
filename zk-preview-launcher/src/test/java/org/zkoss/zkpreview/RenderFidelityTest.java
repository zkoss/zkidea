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
        assertTrue(html.contains("vm.rows"),
                () -> "model placeholders: the listbox's @load('vm.rows') model binding must now "
                        + "render synthetic placeholder rows (was empty pre-model-placeholders): " + html);
        assertTrue(tracker.getAttempts().isEmpty(),
                "the isolation hook must intercept before ZK ever attempts to resolve the ViewModel class, "
                        + "but attempts were recorded: " + tracker.getAttempts());
    }

    @ParameterizedTest(name = "(g) binding-model.zul [{0}]")
    @MethodSource("variants")
    void fixtureG_boundModelRendersPlaceholderRowsFromTemplate(Variants.Named variant) throws Exception {
        ForbiddenLoadTracker tracker = new ForbiddenLoadTracker(CANARY_PREFIX);
        RenderResult r = render(variant, "binding-model.zul", tracker);
        assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describeFailure(r));
        String html = r.getHtml();
        // the grid's static column structure renders
        assertTrue(html.contains("Name"), () -> "column header must render: " + html);
        assertTrue(html.contains("Price"), () -> "column header must render: " + html);
        // a synthetic model makes ZK render the <template name="model"> server-side, N rows,
        // and M-1 fills each cell's @load(each.*) with placeholder text
        assertTrue(count(html, "each.name") >= 3,
                () -> "model placeholders: expected >= 3 template rows binding each.name: " + html);
        assertTrue(count(html, "each.price") >= 3,
                () -> "model placeholders: expected >= 3 template rows binding each.price: " + html);
        assertFalse(html.contains("LOADED"), "real bound value must not leak: " + html);
        assertFalse(html.contains("CANARY"), "real bound value must not leak: " + html);
        assertTrue(tracker.getAttempts().isEmpty(),
                "no user class may be loaded to synthesize the model: " + tracker.getAttempts());
    }

    @ParameterizedTest(name = "(h) binding-model-rows.zul [{0}]")
    @MethodSource("variants")
    void fixtureH_boundModelWithExplicitRowsRenders(Variants.Named variant) throws Exception {
        ForbiddenLoadTracker tracker = new ForbiddenLoadTracker(CANARY_PREFIX);
        RenderResult r = render(variant, "binding-model-rows.zul", tracker);
        // Regression: injecting the model before the explicit <rows> composed threw
        // "Only one rows child is allowed"; it must be injected post-composition.
        assertTrue(r.isSuccess(),
                () -> "explicit <rows><template> + model must render, not fail: " + describeFailure(r));
        String html = r.getHtml();
        assertTrue(count(html, "each.name") >= 3,
                () -> "expected >= 3 placeholder rows via the explicit <rows> template: " + html);
        assertFalse(html.contains("LOADED"), "real bound value must not leak: " + html);
        assertTrue(tracker.getAttempts().isEmpty(),
                "no user class may be loaded: " + tracker.getAttempts());
    }

    @ParameterizedTest(name = "(i) binding-tree.zul [{0}]")
    @MethodSource("variants")
    void fixtureI_boundTreeRendersPlaceholderNodes(Variants.Named variant) throws Exception {
        ForbiddenLoadTracker tracker = new ForbiddenLoadTracker(CANARY_PREFIX);
        RenderResult r = render(variant, "binding-tree.zul", tracker);
        assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describeFailure(r));
        String html = r.getHtml();
        // a synthetic TreeModel makes ZK render the tree's <template name="model">; the
        // top-level nodes' @load(node.*) cells are filled by the text-placeholder pass
        assertTrue(count(html, "node.data.label") >= 3,
                () -> "tree placeholders: expected >= 3 top-level node cells: " + html);
        assertFalse(html.contains("LOADED"), "real bound value must not leak: " + html);
        assertFalse(html.contains("CANARY"), "real bound value must not leak: " + html);
        assertTrue(tracker.getAttempts().isEmpty(),
                "no user class may be loaded to synthesize the tree model: " + tracker.getAttempts());
    }

    @ParameterizedTest(name = "(j) binding-styled.zul [{0}]")
    @MethodSource("variants")
    void fixtureJ_styledBoundLabelIsStillDimmed(Variants.Named variant) throws Exception {
        ForbiddenLoadTracker tracker = new ForbiddenLoadTracker(CANARY_PREFIX);
        RenderResult r = render(variant, "binding-styled.zul", tracker);
        assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describeFailure(r));
        String html = r.getHtml();
        assertTrue(html.contains("vm.greeting"),
                () -> "M-1: the @load value should render as placeholder text: " + html);
        // C2 (S/C review): dimming must be APPLIED even though the label already carries an inline
        // style (the buggy version skipped dimming whenever a style was present), and it must be
        // APPENDED so the author's own style survives -- both the dim colour (#9aa0a6) and the
        // author's background (#ff0000) must appear. Markers are hyphen-free (lesson #14).
        assertTrue(html.contains("9aa0a6"),
                () -> "C2: placeholder dim colour must be applied to a styled bound component: " + html);
        assertTrue(html.contains("ff0000"),
                () -> "C2: the author's own inline style must be preserved, not clobbered: " + html);
        assertFalse(html.contains("LOADED"), "real bound value must not leak: " + html);
        assertTrue(tracker.getAttempts().isEmpty(),
                "no user class may be loaded: " + tracker.getAttempts());
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
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
