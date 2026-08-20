package org.zkoss.zkpreview;

import org.zkoss.zkpreview.mockcore.MockHttpServletRequestCore;
import org.zkoss.zkpreview.mockcore.MockHttpServletResponseCore;
import org.zkoss.zkpreview.mockcore.MockServletContextCore;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servlet-namespace-agnostic core of the render engine (review M1, Template Method). It owns the
 * entire drive logic — classloader isolation, bootstrapping {@code DHtmlLayoutServlet} (page render)
 * and {@code DHtmlUpdateServlet} (resource serving under {@code /zkau/web/*}) by reflection, the
 * render and resource service calls, the AU stub and shutdown — referencing only JDK types and the
 * {@code mockcore} bases. Everything whose type is tied to a servlet namespace — constructing the
 * mock servlet objects, the {@code ServletContextEvent}, and the {@code ServletRequest}/
 * {@code ServletResponse}/{@code ServletConfig}/{@code ServletContextEvent} class literals reflection
 * needs — is a {@code protected} seam the {@code jakarta}/{@code javax} subclasses fill in.
 *
 * <p>Companion to the {@code mockcore} Bridge: the same drift-prone logic that used to be copied per
 * namespace now lives in exactly one place. The subclasses hold no state and only construct their
 * own-namespace mocks, so the base constructor may safely call the seams during bootstrap.
 */
public abstract class AbstractRenderEngine implements RenderEngine {

    /** ZK's library-wide "how often to re-stat a cached resource" knob (unit: seconds). */
    private static final String CHECK_PERIOD_PROPERTY = "org.zkoss.util.resource.checkPeriod";

    /** Longest controller-failure line put on a response header / into a warning. */
    private static final int FAILURE_LINE_LIMIT = 300;

    private final ScopedZkClassLoader zkLoader;
    private final ControllerPolicy controllerPolicy;
    /** {@code IsolationScope.set(boolean)} / {@code .clear()} on the hooks classes, reached by
     * reflection for the same reason the servlets above are: the hook classes are compiled
     * against ZK and live on the scoped ZK classloader, so the main sourceSet must not -- and
     * cannot -- link against them. */
    private final Method isolationScopeSet;
    private final Method isolationScopeClear;
    /** Shared mock context created by the subclass; downcast back to the adapter type inside the seams. */
    protected final MockServletContextCore servletContext;
    private final Object layoutServlet;
    private final Method layoutServiceMethod;
    private final Object updateServlet;
    private final Method updateServiceMethod;

    protected AbstractRenderEngine(List<File> zkJars, Path webappDir, ForbiddenLoadTracker forbiddenLoadTracker) {
        this(zkJars, webappDir, forbiddenLoadTracker, ControllerPolicy.fromProcessDefault());
    }

    protected AbstractRenderEngine(List<File> zkJars, Path webappDir, ForbiddenLoadTracker forbiddenLoadTracker,
            ControllerPolicy controllerPolicy) {
        alwaysRecheckSourcesOnDisk();
        this.controllerPolicy = controllerPolicy;
        this.zkLoader = IsolatedRuntime.buildZkClassLoader(zkJars, getClass().getClassLoader(),
                forbiddenLoadTracker);
        this.servletContext = createServletContext(webappDir);

        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(zkLoader);
        try {
            Class<?> listenerCls = zkLoader.loadClass("org.zkoss.zk.ui.http.HttpSessionListener");
            Object listener = listenerCls.getConstructor().newInstance();
            listenerCls.getMethod("contextInitialized", servletContextEventClass())
                    .invoke(listener, newServletContextEvent(servletContext));

            Class<?> layoutCls = zkLoader.loadClass("org.zkoss.zk.ui.http.DHtmlLayoutServlet");
            layoutServlet = layoutCls.getConstructor().newInstance();
            Object layoutConfig = createServletConfig("zkLoader", servletContext,
                    Map.of("update-uri", "/zkau", "compress", "false"));
            layoutCls.getMethod("init", servletConfigClass()).invoke(layoutServlet, layoutConfig);
            layoutServiceMethod = layoutCls.getMethod("service", servletRequestClass(), servletResponseClass());

            Class<?> updateCls = zkLoader.loadClass("org.zkoss.zk.au.http.DHtmlUpdateServlet");
            updateServlet = updateCls.getConstructor().newInstance();
            Object updateConfig = createServletConfig("auEngine", servletContext, Map.of());
            updateCls.getMethod("init", servletConfigClass()).invoke(updateServlet, updateConfig);
            updateServiceMethod = updateCls.getMethod("service", servletRequestClass(), servletResponseClass());

            Class<?> scopeCls = zkLoader.loadClass("org.zkoss.zkpreview.hooks.IsolationScope");
            isolationScopeSet = scopeCls.getMethod("set", boolean.class);
            isolationScopeClear = scopeCls.getMethod("clear");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to bootstrap the ZK mock webapp", e);
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    /**
     * Makes ZK re-stat a cached page definition on every request instead of trusting it for the
     * next 5 seconds ({@code ResourceCache}'s default check period).
     *
     * <p>Server-side staleness is invisible in a real webapp -- the user just hits reload -- but
     * fatal here, because the preview pane reloads only on save (AC-5): an edit saved inside the
     * window is answered from the cache, and no further event ever arrives to correct the pane.
     * The failure is also perfectly camouflaged, since a page whose parse threw is never cached:
     * fixing a broken ZUL always refreshes, and it is the <em>next</em> edit that silently doesn't.
     *
     * <p>A non-positive value disables the window (ZK keeps the cache entry but compares
     * {@code lastModified} every time, so an unchanged file still isn't re-parsed). It cannot be
     * set through the bundled {@code zk.xml}: {@code <file-check-period>} is parsed as
     * positive-only. Set only when absent, so an explicit {@code -D} on the CLI still wins.
     */
    private static void alwaysRecheckSourcesOnDisk() {
        if (System.getProperty(CHECK_PERIOD_PROPERTY) == null) {
            System.setProperty(CHECK_PERIOD_PROPERTY, "-1");
        }
    }

    /**
     * One render attempt in one mode (P0-2). {@code isolated} is scoped to the rendering thread
     * via the hooks' {@code IsolationScope} rather than a system property: renders are concurrent
     * (PreviewHttpServer's 8-thread pool) and a JVM-global flag flipped for one of them would
     * corrupt the others -- including a plugin preview that never asked to leave isolation.
     */
    private RenderResult renderOnce(String zulPath, boolean isolated) {
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(zkLoader);
        try {
            isolationScopeSet.invoke(null, isolated);
            MockHttpServletRequestCore req = createRequest(zulPath, null, "GET");
            MockHttpServletResponseCore resp = createResponse();
            layoutServiceMethod.invoke(layoutServlet, req, resp);
            return RenderResult.success(resp.getContent());
        } catch (InvocationTargetException e) {
            return RenderResult.failure(ErrorMapper.map(zulPath, e.getCause() != null ? e.getCause() : e));
        } catch (Exception e) {
            return RenderResult.failure(ErrorMapper.map(zulPath, e));
        } finally {
            try {
                isolationScopeClear.invoke(null);
            } catch (ReflectiveOperationException ignored) {
                // Nothing actionable: the thread-local is only read by the hooks, and the next
                // render on this thread sets it again before reading it.
            }
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    /**
     * Renders once in the mode {@link ControllerPolicy} asks for, degrading to isolation if the
     * project's controllers cannot deliver (P0-2 items 5 and 6).
     *
     * <p>Isolated -- the default, and everything the IntelliJ plugin ever does -- is today's path
     * exactly: the calling thread, no executor, no budget. The budget deliberately applies only
     * when controllers run, so an isolated render can never newly time out; it is also a
     * whole-render wall clock rather than controller-only time, because the two are inseparable
     * inside one {@code service} call (see {@link ControllerPolicy}).
     *
     * <p>Controllers on: run the attempt on a one-shot daemon thread so a controller that hangs
     * cannot hold the HTTP handler thread forever. A hung attempt can only be interrupted, never
     * killed -- a busy-looping controller keeps its thread and its ZK desktop for the life of the
     * process -- so the thread is <em>abandoned</em>: acceptable for a one-shot preview JVM, and
     * harmless at exit because the pool's threads are daemons.
     *
     * <p>No exception classification: any failed controllers-on attempt is retried isolated, and
     * the two attempts are then <em>compared</em> -- that comparison, not a list of exception
     * types, is what decides whether a controller caused the failure. If the isolated retry
     * succeeds, the defect only exists while the controllers run, so it is theirs
     * ({@link ControllerOutcome#FAILED} plus the cause line). If the isolated retry fails too, the
     * same defect is there with the controllers standing down, so it is the page's: the isolated
     * attempt's {@link RenderError} is reported with <em>no</em> controller claim at all
     * ({@link ControllerOutcome#SKIPPED}, no cause line), and the error page and
     * {@code preview-zul.py}'s exit 1 for a genuinely broken ZUL read exactly as they do today.
     */
    @Override
    public RenderResult renderZul(String zulPath) {
        if (!controllerPolicy.runControllers()) {
            // A policy that asked for isolation explicitly (--isolation on, ControllerPolicy.of
            // with runControllers=false) means it: it must win over the process-wide property.
            // Only the un-asked-for default falls through to the property, so the AC-4 canary --
            // -Dzkpreview.isolation=false with no CLI option and no explicit policy -- keeps
            // reaching the hooks raw: no executor, no budget, failures surfaced as failures.
            boolean isolated = controllerPolicy.forceIsolated() || IsolationMode.isEnabled();
            return renderOnce(zulPath, isolated).withControllers(
                    isolated ? ControllerOutcome.SKIPPED : ControllerOutcome.EXECUTED, null);
        }
        ExecutorService oneShot = Executors.newSingleThreadExecutor(controllerThreadFactory());
        try {
            Future<RenderResult> attempt = oneShot.submit(() -> renderOnce(zulPath, false));
            try {
                RenderResult r = attempt.get(controllerPolicy.timeoutSeconds(), TimeUnit.SECONDS);
                if (r.isSuccess()) {
                    return r.withControllers(ControllerOutcome.EXECUTED, null);
                }
                return retryIsolated(zulPath, controllerFailureLine(r.getError()));
            } catch (TimeoutException e) {
                attempt.cancel(true);
                return retryIsolated(zulPath, "the render exceeded the " + controllerPolicy.timeoutSeconds()
                        + "s controller budget (the budget covers the whole render, not controller time"
                        + " alone; raise it with --controller-timeout)");
            } catch (ExecutionException e) {
                return retryIsolated(zulPath, oneLineCause(e.getCause() != null ? e.getCause() : e));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return retryIsolated(zulPath, "interrupted while running the project's controllers");
            }
        } finally {
            // Interrupts an abandoned attempt if it is sleeping/waiting; a busy one simply
            // outlives this call on its daemon thread.
            oneShot.shutdownNow();
        }
    }

    /**
     * The fail-soft half of {@link #renderZul}: today's isolated render, and the comparison that
     * decides whether {@code failure} may be reported as a controller's.
     *
     * <p>P0-2 item 5 scopes both {@code CONTROLLERS: failed -> isolated} and its WARNINGS entry to
     * "a page-level failure caused by user controller code", and the reader is told to go and fix
     * the controller. A ZUL that is broken on its own fails identically with the controllers
     * standing down, so a retry that fails too proves nothing was the controller's fault: claiming
     * one would send the reader to the wrong file. Such a result is returned as the plain isolated
     * failure -- outcome {@code SKIPPED}, no cause line -- which is byte-for-byte the report a
     * broken ZUL gets without {@code --run-controllers}.
     */
    private RenderResult retryIsolated(String zulPath, String failure) {
        RenderResult isolated = renderOnce(zulPath, true);
        if (!isolated.isSuccess()) {
            System.err.println("[zk-preview] the isolated retry of " + zulPath
                    + " failed too, so the failure is not controller-caused; reporting it as-is."
                    + " The controllers-on attempt said: " + failure);
            return isolated.withControllers(ControllerOutcome.SKIPPED, null);
        }
        System.err.println("[zk-preview] controllers failed for " + zulPath
                + ", retrying isolated: " + failure);
        return isolated.withControllers(ControllerOutcome.FAILED, failure);
    }

    private static ThreadFactory controllerThreadFactory() {
        AtomicInteger seq = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "zk-preview-controllers-" + seq.incrementAndGet());
            // Daemon: a controller that never returns must not keep this JVM alive after the
            // caller kills the server (the thread cannot be stopped, only abandoned).
            t.setDaemon(true);
            return t;
        };
    }

    /**
     * The reported cause of a failed controllers-on attempt: ZK's own message, plus the first
     * project frame from the stack when there is one.
     *
     * <p>The frame matters because ZK's message frequently does not name the controller at all --
     * a composer throwing {@code IllegalStateException("...")} produces a message about the
     * exception and nothing about who threw it -- and "which of this page's controllers failed"
     * is the first thing the reader needs (P0-2 item 5: name the class and the first cause line).
     * Infrastructure frames are skipped so the named class is the project's, not ZK's; when there
     * is no project frame at all (a {@code ClassNotFoundException}, a zscript failure) the message
     * already carries the class name and nothing is appended.
     */
    static String controllerFailureLine(RenderError error) {
        if (error == null) {
            return "unknown controller failure";
        }
        String message = oneLine(error.getMessage());
        String frame = firstProjectFrame(error.getStackTrace());
        if (frame == null) {
            return message == null ? "unknown controller failure" : message;
        }
        return oneLine((message == null ? "controller failure" : message) + " (in " + frame + ")");
    }

    /** Frames of ZK, the JDK, the launcher and the script engines -- never the caller's own code. */
    private static final List<String> INFRASTRUCTURE_PACKAGES = List.of(
            "org.zkoss.", "java.", "javax.", "jakarta.", "jdk.", "sun.", "com.sun.", "bsh.");

    /** One stack-trace frame's declaring class. The optional {@code <prefix>/} part is the module
     * or classloader name the JDK prints for a class loaded outside the app loader -- every ZK and
     * project frame here carries {@code zk-preview-scoped//}, so ignoring it is not optional. */
    private static final java.util.regex.Pattern STACK_FRAME =
            java.util.regex.Pattern.compile("\\bat (?:\\S*/)?([A-Za-z_$][\\w.$]*)\\.[\\w$<>]+\\(");

    /** First class named in {@code stackTrace} that is not infrastructure; {@code null} if none. */
    static String firstProjectFrame(String stackTrace) {
        if (stackTrace == null) {
            return null;
        }
        java.util.regex.Matcher m = STACK_FRAME.matcher(stackTrace);
        while (m.find()) {
            String cls = m.group(1);
            boolean infrastructure = false;
            for (String prefix : INFRASTRUCTURE_PACKAGES) {
                if (cls.startsWith(prefix)) {
                    infrastructure = true;
                    break;
                }
            }
            if (!infrastructure) {
                return cls;
            }
        }
        return null;
    }

    /** Exception class name plus its first message line, collapsed to one bounded line -- it ends
     * up on an HTTP response header and in a one-line WARNINGS entry. */
    static String oneLineCause(Throwable cause) {
        if (cause == null) {
            return "unknown controller failure";
        }
        String message = oneLine(cause.getMessage());
        return message == null ? cause.getClass().getName() : cause.getClass().getName() + ": " + message;
    }

    /** First line of {@code text}, whitespace-collapsed and capped; {@code null} stays {@code null}.
     * The cap marker is ASCII "..." rather than U+2026 on purpose: this line's only destinations
     * are an HTTP response header and the WARNINGS entry built from it, and
     * {@code PreviewHttpServer.headerSafe} transliterates every non-ASCII char to {@code '?'} --
     * a truncated message would otherwise reach the reader ending in a bare question mark. */
    static String oneLine(String text) {
        if (text == null) {
            return null;
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        if (flat.isEmpty()) {
            return null;
        }
        return flat.length() <= FAILURE_LINE_LIMIT ? flat : flat.substring(0, FAILURE_LINE_LIMIT - 3) + "...";
    }

    @Override
    public ResourceResult resource(String pathInfo) {
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(zkLoader);
        try {
            MockHttpServletRequestCore req = createRequest("/zkau", pathInfo, "GET");
            MockHttpServletResponseCore resp = createResponse();
            updateServiceMethod.invoke(updateServlet, req, resp);
            return resourceOutcome(pathInfo, resp.getStatus(), resp.getContentType(), resp.getContentBytes());
        } catch (Exception e) {
            return resourceFailure(pathInfo, e);
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    /**
     * Classifies a completed {@code /zkau/web/*} fetch, reporting a &ge;400 on stderr before
     * collapsing it to a 404 (review R2-CRIT2). The collapse itself is intentional and unchanged --
     * a failed asset must not paint a ZK error body into a {@code <script>}/{@code <link>} slot --
     * but it used to be indistinguishable from a typo'd path, which made "my preview renders
     * unstyled" undebuggable: no log, no stderr, no distinguishing status, and (because the page
     * itself rendered fine) no error card and so no Report link either.
     *
     * <p>Package-visible and platform-free so both branches are unit-testable without a ZK jar.
     */
    static ResourceResult resourceOutcome(String pathInfo, int status, String contentType, byte[] body) {
        if (status >= 400) {
            System.err.println("[zk-preview] resource " + pathInfo + " -> HTTP " + status
                    + " (serving 404); body: " + snippet(body));
            return ResourceResult.notFound();
        }
        return ResourceResult.of(status, contentType, body);
    }

    /** Reports a thrown {@code /zkau/web/*} fetch on stderr before collapsing it to a 404 (R2-CRIT2). */
    static ResourceResult resourceFailure(String pathInfo, Throwable cause) {
        // Unwrap like renderZul does: the reflective Method.invoke wrapper hides the ZK
        // exception that actually explains the failure.
        Throwable real = (cause instanceof InvocationTargetException && cause.getCause() != null)
                ? cause.getCause() : cause;
        System.err.println("[zk-preview] resource " + pathInfo + " failed (serving 404): " + real);
        real.printStackTrace(System.err);
        return ResourceResult.notFound();
    }

    /** First line of a failed asset's body, bounded -- enough to recognise a ZK error page. */
    private static String snippet(byte[] body) {
        if (body == null || body.length == 0) return "<empty>";
        String text = new String(body, 0, Math.min(body.length, 200), StandardCharsets.UTF_8);
        int nl = text.indexOf('\n');
        return (nl >= 0 ? text.substring(0, nl) : text).trim();
    }

    @Override
    public byte[] auStub() {
        // Valid empty AU response envelope. The preview is a one-shot render with no
        // live desktop, so any interaction (expand a tree node, sort a grid, page a
        // listbox) fires an AU POST we cannot fulfil. The ZK client JSON.parse()es the
        // response (zAu.pushReqCmds), so it must be a JSON object with an empty "rs"
        // command list -- the client then runs zero commands (an inert no-op) instead
        // of showing "Expected JSON format ... Unexpected token '<'". rid:0 is falsy on
        // the client, so the empty command set is applied without a sequence check.
        return "{\"rid\":0,\"rs\":[]}".getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws java.io.IOException {
        zkLoader.close();
    }

    // --- servlet-namespace seams (filled by the jakarta/javax subclasses) ---

    /** Builds the shared mock {@code ServletContext} of this engine's namespace. */
    protected abstract MockServletContextCore createServletContext(Path webappDir);

    /** {@code ServletContextEvent.class} of this namespace, for the {@code contextInitialized} lookup. */
    protected abstract Class<?> servletContextEventClass();

    /** {@code new ServletContextEvent(ctx)} of this namespace. */
    protected abstract Object newServletContextEvent(MockServletContextCore ctx);

    /** {@code ServletConfig.class} of this namespace, for the {@code Servlet.init} lookup. */
    protected abstract Class<?> servletConfigClass();

    /** Builds a mock {@code ServletConfig} of this namespace over the shared context. */
    protected abstract Object createServletConfig(String servletName, MockServletContextCore ctx,
            Map<String, String> initParams);

    /** {@code ServletRequest.class} of this namespace, for the {@code Servlet.service} lookup. */
    protected abstract Class<?> servletRequestClass();

    /** {@code ServletResponse.class} of this namespace, for the {@code Servlet.service} lookup. */
    protected abstract Class<?> servletResponseClass();

    /** Builds a mock request over a fresh session (one session per call — see the subclass {@code newSession}). */
    protected abstract MockHttpServletRequestCore createRequest(String servletPath, String pathInfo, String method);

    /** Builds a mock response of this namespace whose captured body the base reads back. */
    protected abstract MockHttpServletResponseCore createResponse();
}
