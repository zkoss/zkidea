package org.zkoss.zkpreview;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Child-first (for {@code org.zkoss.*}) classloader used to load a specific,
 * caller-supplied ZK version plus the injected isolation-hook classes.
 *
 * <p>Child-first for {@code org.zkoss.*} lets the resolved ZK jars win over any
 * ZK classes that might otherwise be visible through the parent chain (mirrors
 * the existing spike's {@code ZkMockRenderer} loader).
 *
 * <p>If a {@link ForbiddenLoadTracker} is supplied, any class name matching one
 * of its forbidden prefixes is recorded and rejected outright -- used by the
 * AC-4 isolation tests as defense-in-depth on top of "the class is genuinely
 * unreachable from this loader's classpath".
 *
 * <p><b>Thread safety (review R2-CRIT1).</b> One instance is shared by every request
 * thread of a preview server, and ZK loads component/util classes lazily on those
 * threads, so the child-first branch below must hold {@code getClassLoadingLock(name)}
 * exactly like the JDK's own {@code ClassLoader.loadClass} does -- otherwise two
 * threads both first to touch a class both reach {@code defineClass} and the loser
 * gets {@code LinkageError: attempted duplicate class definition}. Registering as
 * parallel-capable keeps that lock per class name rather than per loader, so the
 * burst of concurrent {@code /zkau/web/*} fetches a single page produces still loads
 * distinct classes in parallel.
 */
public final class ScopedZkClassLoader extends URLClassLoader {

    static {
        registerAsParallelCapable();
    }

    private final ForbiddenLoadTracker forbiddenLoadTracker;

    public ScopedZkClassLoader(URL[] urls, ClassLoader parent, ForbiddenLoadTracker forbiddenLoadTracker) {
        super("zk-preview-scoped", urls, parent);
        this.forbiddenLoadTracker = forbiddenLoadTracker;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (forbiddenLoadTracker != null && forbiddenLoadTracker.isForbidden(name)) {
            forbiddenLoadTracker.recordAttempt(name);
            throw new ClassNotFoundException(name + " is on the forbidden-load list (isolation test)");
        }
        if (name.startsWith("org.zkoss.")) {
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    try {
                        c = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        // Fall through to standard parent-first delegation below.
                    }
                }
                if (c != null) {
                    if (resolve) resolveClass(c);
                    return c;
                }
            }
        }
        return super.loadClass(name, resolve);
    }
}
