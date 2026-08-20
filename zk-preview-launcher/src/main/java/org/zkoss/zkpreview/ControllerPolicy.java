package org.zkoss.zkpreview;

/**
 * Per-engine decision about the project's controllers: run them, and for how long. Immutable,
 * JDK types only, so it can travel from {@link Main} through {@link RenderEngineFactory} into
 * {@link AbstractRenderEngine} without adding a parameter to the {@link RenderEngine} interface
 * (~25 existing {@code create(jars, webapp, tracker)} call sites keep compiling).
 *
 * <p><b>The budget is a whole-render wall clock, not controller-only time.</b> The launcher cannot
 * separate ZK's own first-paint work (language/component definitions, page compile, serialization)
 * from what a Composer's {@code doAfterCompose} spends, because both happen inside the one
 * {@code DHtmlLayoutServlet.service} call the engine makes. A cold JVM's first render is the
 * expensive one, so on a slow machine the budget can expire on ZK rather than on the controller;
 * the timeout path therefore must always say so in its reported failure line and name
 * {@code --controller-timeout} (risk R1).
 *
 * <p>{@link #fromProcessDefault()} is deliberately isolated regardless of
 * {@code -Dzkpreview.isolation}: the system property stays the raw, un-wrapped hooks-level switch
 * (no executor, no budget, no fail-soft retry) that the AC-4 canary tests depend on, while
 * controller execution with the fail-soft wrapper is only ever entered when a caller asks for it
 * explicitly -- {@code --isolation off} on the CLI, or {@link #of} in a test.
 *
 * <p>Isolation therefore has two distinct sources, which {@link #forceIsolated()} tells apart:
 * the default nobody asked about (the property may still switch the hooks off underneath it, which
 * is exactly what the canary needs), and an explicit {@code --isolation on} / {@code of(false, n)}
 * that the property must not be able to override.
 */
public final class ControllerPolicy {

    /** P0-2 item 6: default per-render controller budget, in seconds. */
    public static final int DEFAULT_TIMEOUT_SECONDS = 10;

    private final boolean runControllers;
    private final boolean forceIsolated;
    private final int timeoutSeconds;

    private ControllerPolicy(boolean runControllers, boolean forceIsolated, int timeoutSeconds) {
        this.runControllers = runControllers;
        this.forceIsolated = forceIsolated;
        this.timeoutSeconds = timeoutSeconds;
    }

    /** The safe default every existing caller (and the IntelliJ plugin) gets: isolated, but with
     * the raw process property still able to switch the hooks off underneath it (the AC-4 canary). */
    public static ControllerPolicy fromProcessDefault() {
        return new ControllerPolicy(false, false, DEFAULT_TIMEOUT_SECONDS);
    }

    /** An explicit choice: {@code of(false, n)} pins isolation on even if the process property
     * says otherwise, so {@code --isolation on} actually enforces isolation. */
    public static ControllerPolicy of(boolean runControllers, int timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("controller timeout must be positive: " + timeoutSeconds);
        }
        return new ControllerPolicy(runControllers, !runControllers, timeoutSeconds);
    }

    public boolean runControllers() {
        return runControllers;
    }

    /** {@code true} when isolation was asked for explicitly, so {@code -Dzkpreview.isolation=false}
     * must not be consulted for this render. */
    public boolean forceIsolated() {
        return forceIsolated;
    }

    public int timeoutSeconds() {
        return timeoutSeconds;
    }
}
