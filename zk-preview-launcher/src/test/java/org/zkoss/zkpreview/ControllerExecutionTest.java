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
 * P0-2: the mirror of {@link IsolationTest}'s AC-4(iii). Where that test proves the hooks are why
 * nothing loads, this one proves that with the hooks off the project's controllers really are
 * loaded and run, that their values reach the rendered page, and that a controller which throws,
 * hangs or was never compiled degrades the preview to an isolated render instead of destroying it.
 *
 * <p>No {@link ForbiddenLoadTracker} here, deliberately: the canary classes are reachable through
 * the scoped loader's parent in-process, which is exactly what makes this the inverse of the
 * AC-4(iii) setup. Every controllers-on case asserts a <em>real value</em> in the HTML rather than
 * merely "no exception" -- a thread-scoped mode that silently failed to reach the hooks would
 * otherwise pass while rendering placeholders (risk R3).
 *
 * <p>Together the cases cover the whole P0-2 item-7 matrix: (a)/(b) the controllers-on column,
 * (c) the controller-failed column re-using the isolated column's assertions verbatim, and
 * (f) the isolated column plus the guarantee that the budget is not applied to it. (g)/(h) guard
 * the other direction: a page that is broken on its own must still fail, in the same shape and
 * without being blamed on a controller.
 */
class ControllerExecutionTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");

    /** The dim colour {@code PlaceholderInjector} styles placeholders with -- its presence is the
     * cheapest proof that the injector ran, i.e. that isolation was on. */
    private static final String DIM_COLOUR = "#9aa0a6";

    static Stream<Variants.Named> variants() {
        return Variants.both();
    }

    /**
     * ZK's {@code zkmx} bootstrap writes property values as JavaScript string literals and escapes
     * {@code -} as {@code \-} on ZK 10 (so a value can never close a comment or CDATA section) but
     * not on ZK 9. Compare against a de-escaped copy so one assertion covers both variants;
     * {@code RenderFidelityTest} sidesteps this by only ever matching hyphen-free fragments.
     */
    private static String unescaped(String html) {
        return html.replace("\\-", "-");
    }

    // (a) AC-2: a working ViewModel's bound value replaces the placeholder entirely.
    @ParameterizedTest(name = "(a) viewmodel-bind.zul runs the ViewModel [{0}]")
    @MethodSource("variants")
    void controllersOnRendersRealBoundValue(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        try (RenderEngine engine = controllersOn(res.jars, 30)) {
            RenderResult r = engine.renderZul("/viewmodel-bind.zul");
            assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describe(r));
            assertEquals(ControllerOutcome.EXECUTED, r.getControllers(), () -> describe(r));
            String html = unescaped(r.getHtml());
            assertTrue(html.contains("LOADED-CANARY-VALUE"),
                    () -> "the ViewModel's real value must reach the page: " + html);
            assertFalse(html.contains("vm.greeting"),
                    () -> "no placeholder text may remain when the Binder resolved the value: " + html);
            assertFalse(html.contains(DIM_COLOUR),
                    () -> "the placeholder injector must stand down with isolation off: " + html);
        }
    }

    // (b) item 7, middle column: real rows from the model, not synthetic placeholder rows.
    @ParameterizedTest(name = "(b) binding-model.zul renders real rows [{0}]")
    @MethodSource("variants")
    void controllersOnRendersRealModelRows(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        try (RenderEngine engine = controllersOn(res.jars, 30)) {
            RenderResult r = engine.renderZul("/binding-model.zul");
            assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describe(r));
            assertEquals(ControllerOutcome.EXECUTED, r.getControllers(), () -> describe(r));
            String html = unescaped(r.getHtml());
            assertTrue(html.contains("CANARY-ROW-A") && html.contains("CANARY-ROW-B"),
                    () -> "the model's real rows must be rendered: " + html);
            assertTrue(html.contains("19.99"), () -> "per-cell bindings must resolve: " + html);
            assertFalse(html.contains("vm.rows"),
                    () -> "no model placeholder rows may remain: " + html);
        }
    }

    // (c) AC-3 unit mirror + item 7's third column: a controller that throws falls all the way
    // back to the isolated column's assertions.
    @ParameterizedTest(name = "(c) a throwing controller degrades to isolated [{0}]")
    @MethodSource("variants")
    void throwingControllerFallsBackToIsolatedRender(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        try (RenderEngine engine = controllersOn(res.jars, 30)) {
            RenderResult r = engine.renderZul("/controllers-failing.zul");
            assertTrue(r.isSuccess(), () -> "a controller failure must not destroy the preview: " + describe(r));
            assertEquals(ControllerOutcome.FAILED, r.getControllers(), () -> describe(r));
            String failure = r.getControllerFailure();
            assertNotNull(failure, "the failure line is what the caller reports in WARNINGS");
            assertTrue(failure.contains("IllegalStateException"), failure);
            assertTrue(failure.contains("canary controller failure")
                            || failure.contains("FailingViewModel"), failure);
            // The isolated column, asserted exactly as it is for the default mode.
            String html = r.getHtml();
            assertTrue(html.contains("vm.greeting"),
                    () -> "the isolated retry must place the placeholder text back: " + html);
            assertTrue(html.contains(DIM_COLOUR),
                    () -> "the isolated retry must dim its placeholders: " + html);
            assertTrue(html.contains("static sibling"), html);
        }
    }

    // (d) AC-4 unit mirror: a controller that sleeps 60 s is abandoned at the budget.
    @ParameterizedTest(name = "(d) a hanging controller hits the budget [{0}]")
    @MethodSource("variants")
    void hangingControllerHitsTheBudgetAndDegrades(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        RenderEngine engine = controllersOn(res.jars, 2);
        try {
            long startedAt = System.nanoTime();
            RenderResult r = engine.renderZul("/controllers-sleeping.zul");
            long seconds = (System.nanoTime() - startedAt) / 1_000_000_000L;
            assertTrue(r.isSuccess(), () -> "a hung controller must not destroy the preview: " + describe(r));
            assertEquals(ControllerOutcome.FAILED, r.getControllers(), () -> describe(r));
            assertTrue(r.getControllerFailure().contains("budget"), r.getControllerFailure());
            assertTrue(seconds < 30, "the 60s sleep must be abandoned at the budget, took " + seconds + "s");
            assertTrue(r.getHtml().contains("static sibling"), r.getHtml());
        } finally {
            // This is the only case that abandons a render thread, so it is the only one that must
            // not close the engine straight away: shutdownNow() interrupts the 60 s sleeper, ZK
            // then keeps composing on that thread and resolves further classes through the
            // engine's ScopedZkClassLoader. Closing the loader underneath it turns those into
            // ClassNotFoundExceptions printed to stderr after this test has already finished,
            // which corrupts Gradle's binary test-output store and fails the whole task while
            // writing its JUnit XML (losing reports for unrelated suites). Wait for the abandoned
            // thread to end first; if it somehow outlives the wait, leave the engine open rather
            // than pull its classloader out from under a live thread -- one leaked engine in a
            // forked test JVM is harmless, a corrupted report store is not.
            if (awaitAbandonedControllerThreads()) {
                engine.close();
            }
        }
    }

    /** @return {@code true} once no launcher controller thread is alive any more. */
    private static boolean awaitAbandonedControllerThreads() throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
        while (controllerThreadAlive()) {
            if (System.nanoTime() > deadline) {
                return false;
            }
            Thread.sleep(100L);
        }
        return true;
    }

    /** Named by {@code AbstractRenderEngine.controllerThreadFactory()}. */
    private static boolean controllerThreadAlive() {
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.isAlive() && t.getName().startsWith("zk-preview-controllers-")) {
                return true;
            }
        }
        return false;
    }

    // (e) AC-5 unit mirror: a controller class that was never compiled.
    @ParameterizedTest(name = "(e) an uncompiled controller degrades with an actionable cause [{0}]")
    @MethodSource("variants")
    void uncompiledControllerDegradesWithTheMissingClassName(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        try (RenderEngine engine = controllersOn(res.jars, 30)) {
            RenderResult r = engine.renderZul("/controllers-uncompiled.zul");
            assertTrue(r.isSuccess(), () -> "a missing controller class must not destroy the preview: " + describe(r));
            assertEquals(ControllerOutcome.FAILED, r.getControllers(), () -> describe(r));
            assertTrue(r.getControllerFailure().contains(
                            "org.zkoss.zkpreview.testcanary.NeverCompiledViewModel"),
                    () -> "the cause must name the missing FQCN: " + r.getControllerFailure());
        }
    }

    // (f) MUST-5: the budget applies only when controllers run. The same fixture whose controller
    // sleeps 60 s renders instantly under the default policy -- no executor, no timeout, no
    // FAILED outcome -- because the ViewModel is never constructed at all.
    @ParameterizedTest(name = "(f) the isolated default never times out [{0}]")
    @MethodSource("variants")
    void isolatedPolicyNeverTimesOutAndKeepsPlaceholders(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        try (RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, null)) {
            RenderResult r = engine.renderZul("/controllers-sleeping.zul");
            assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describe(r));
            assertEquals(ControllerOutcome.SKIPPED, r.getControllers(), () -> describe(r));
            assertNull(r.getControllerFailure());
            String html = r.getHtml();
            assertTrue(html.contains("vm.greeting"), html);
            assertTrue(html.contains(DIM_COLOUR), html);
        }
    }

    // MUST-4's other half: a genuinely broken ZUL still fails, in the same shape as before, so
    // the fail-soft path cannot turn a real defect into a green render.
    @ParameterizedTest(name = "(g) a broken .zul still fails under the isolated default [{0}]")
    @MethodSource("variants")
    void brokenZulStillFailsUnderTheIsolatedDefault(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        ForbiddenLoadTracker tracker = new ForbiddenLoadTracker(
                List.of("org.zkoss.zkpreview.testcanary."));
        try (RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, tracker)) {
            RenderResult r = engine.renderZul("/zscript-missing-class.zul");
            assertFalse(r.isSuccess(), "a broken .zul must still fail");
            assertEquals("/zscript-missing-class.zul", r.getError().getZulFile());
            assertTrue(r.getError().getMessage().contains(
                    "org.zkoss.zkpreview.testcanary.CanaryZscriptTarget"), r.getError().getMessage());
        }
    }

    // (h) The other half of the fail-soft contract: a page that is broken on its own must not be
    // blamed on a controller just because --run-controllers was passed. unsupported-parent.zul
    // (a <row> directly under <window>) fails identically in both modes and has no controller at
    // all, so the reported result must be the plain isolated failure -- SKIPPED, no cause line,
    // ZK's own message -- exactly what the same fixture reports without the flag.
    @ParameterizedTest(name = "(h) a broken .zul is not attributed to a controller [{0}]")
    @MethodSource("variants")
    void brokenZulIsNotReportedAsAControllerFailure(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        try (RenderEngine engine = controllersOn(res.jars, 30)) {
            RenderResult r = engine.renderZul("/unsupported-parent.zul");
            assertFalse(r.isSuccess(), () -> "a broken .zul must still fail: " + describe(r));
            assertEquals(ControllerOutcome.SKIPPED, r.getControllers(),
                    () -> "no controller caused this, so none may be reported: " + describe(r));
            assertNull(r.getControllerFailure(),
                    () -> "a controller-failure warning would send the reader to the wrong file: "
                            + r.getControllerFailure());
            assertTrue(r.getError().getMessage().contains("Unsupported parent for row"),
                    () -> "the isolated attempt's own error is what the caller reports: "
                            + r.getError().getMessage());
        }
    }

    // MUST-1: --isolation on is an explicit decision, so it must beat -Dzkpreview.isolation=false;
    // only the un-asked-for default consults that property (the AC-4 canary depends on it doing so).
    @org.junit.jupiter.api.Test
    void explicitIsolationOnOverridesTheProcessProperty() {
        ControllerPolicy explicit = Main.controllerPolicy(java.util.Map.of("isolation", "on"));
        assertFalse(explicit.runControllers());
        assertTrue(explicit.forceIsolated(),
                "--isolation on must pin isolation instead of deferring to -Dzkpreview.isolation");
        ControllerPolicy unasked = Main.controllerPolicy(java.util.Map.of());
        assertFalse(unasked.runControllers());
        assertFalse(unasked.forceIsolated(),
                "the default must keep letting the canary property through");
    }

    // The frame extraction is what makes "which controller failed" answerable when ZK's own
    // message does not say (the throwing-composer case). Locked here because the JDK prints
    // these frames with a classloader prefix, which a naive pattern silently misses.
    @org.junit.jupiter.api.Test
    void theFirstProjectFrameIsExtractedFromAClassloaderPrefixedTrace() {
        String trace = "java.lang.IllegalStateException: boom\n"
                + "\tat zk-preview-scoped//com.acme.MyComposer.doAfterCompose(MyComposer.java:27)\n"
                + "\tat zk-preview-scoped//org.zkoss.zk.ui.impl.UiEngineImpl.doAfterCompose(UiEngineImpl.java:625)\n"
                + "\tat java.base/java.lang.Thread.run(Thread.java:833)\n";
        assertEquals("com.acme.MyComposer", AbstractRenderEngine.firstProjectFrame(trace));
        // ZK-only frames (a zscript failure, a missing class) must not be reported as the culprit.
        assertNull(AbstractRenderEngine.firstProjectFrame(
                "java.lang.ClassNotFoundException: x\n\tat zk-preview-scoped//org.zkoss.zk.ui.Page.resolveClass(Page.java:1)\n"));
    }

    private static RenderEngine controllersOn(List<java.io.File> jars, int timeoutSeconds) throws Exception {
        return RenderEngineFactory.create(jars, FIXTURES, null, ControllerPolicy.of(true, timeoutSeconds));
    }

    private static String describe(RenderResult r) {
        if (r.isSuccess()) {
            return "SUCCESS controllers=" + r.getControllers() + " failure=" + r.getControllerFailure();
        }
        return "FAILURE controllers=" + r.getControllers() + " failure=" + r.getControllerFailure()
                + " error=" + (r.getError() == null ? "(none)" : r.getError().getMessage());
    }
}
