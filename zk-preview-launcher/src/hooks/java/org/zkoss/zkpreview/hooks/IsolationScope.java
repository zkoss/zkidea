package org.zkoss.zkpreview.hooks;

/**
 * The single gate {@link PreviewUiFactory} and {@link PlaceholderInjector} consult to decide
 * whether isolation is on for the render happening right now.
 *
 * <p>Two inputs, in this order:
 * <ol>
 *   <li>a per-render override set by the launcher around one render call ({@link #set});</li>
 *   <li>otherwise the process-wide default {@code -Dzkpreview.isolation=false}
 *       (mirrors {@code org.zkoss.zkpreview.IsolationMode.SYSTEM_PROPERTY}; duplicated as a
 *       literal so this hooks sourceSet stays compiled against ZK jars only -- no
 *       cross-sourceSet compile dependency on the main launcher).</li>
 * </ol>
 *
 * <p><b>Why a ThreadLocal and not {@code System.setProperty} around each render.</b> The mode is
 * now a per-request choice ({@code --isolation off} plus the fail-soft isolated retry, P0-2), and
 * renders are concurrent: {@code PreviewHttpServer} dispatches on an 8-thread pool
 * ({@code PreviewHttpServer.HANDLER_THREADS}) and {@code PreviewHttpServerConcurrencyTest} locks
 * that in by holding one render open while another completes. Flipping a JVM-global flag for one
 * render would therefore silently change a concurrent one -- including, in the IntelliJ plugin,
 * flipping a live preview out of the isolation it never asked to leave. The plugin shares this
 * code with isolation always on, so the fallback in (2) is what it keeps seeing.
 *
 * <p>ZK composes on the request thread here ({@code <disable-event-thread>true</disable-event-thread>}
 * in the bundled {@code zk.xml}), which is what makes a thread-scoped override reach the hooks at
 * all. If it ever did not, the override would simply be absent and (2) would apply -- isolation on,
 * the safe direction.
 */
public final class IsolationScope {

    private static final String ISOLATION_PROPERTY = "zkpreview.isolation";

    /** Per-render override; {@code null} (absent) means "use the process default". */
    private static final ThreadLocal<Boolean> OVERRIDE = new ThreadLocal<>();

    private IsolationScope() {
    }

    /** Scopes {@code isolated} to the calling thread until {@link #clear}; the launcher pairs
     * the two in a try/finally around exactly one render. */
    public static void set(boolean isolated) {
        OVERRIDE.set(isolated);
    }

    public static void clear() {
        OVERRIDE.remove();
    }

    /** True when the isolation hooks must substitute a no-op composer and inject placeholders. */
    public static boolean isEnabled() {
        Boolean override = OVERRIDE.get();
        if (override != null) {
            return override;
        }
        return !"false".equalsIgnoreCase(System.getProperty(ISOLATION_PROPERTY));
    }
}
