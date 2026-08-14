package org.zkoss.zkpreview;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the {@link ScopedZkClassLoader} a render runs under: the caller-supplied
 * ZK jars plus the injected isolation-hook classes, child-first for
 * {@code org.zkoss.*}, parented on the classloader that is itself running the
 * launcher's own glue code (see caller sites).
 *
 * <p>The parent MUST be the launcher's own defining classloader, not a freshly
 * constructed sibling wrapping the same jar files: mock objects like
 * {@code MockHttpServletRequest} are instantiated directly (plain {@code new})
 * by glue code such as {@code JakartaRenderEngine}, so the {@code jakarta.servlet.*}
 * types they implement must resolve to the exact same {@code Class} objects the
 * scoped loader resolves via parent delegation -- otherwise reflective calls into
 * the ZK servlet fail with e.g. {@code NoSuchMethodException} due to classloader
 * identity mismatch (verified empirically while building this engine).
 *
 * <p>AC-4(i): {@code ScopedZkClassLoader}'s own URL list is exactly what the caller supplies
 * plus the small isolation-hooks jar -- nothing is added here. What the plugin supplies now
 * includes the previewed module's compiled-output roots, so a page's own {@code <zscript>} /
 * {@code use="..."} code can resolve the project's classes; ViewModels and Composers are
 * blocked by the {@code UiFactory} hook, which never resolves their class name.
 * AC-4(ii)/(iii): true parent-narrowness (no unrelated classes reachable at all) is
 * proven by the CLI child-process tests, which invoke the packaged jar as a separate
 * OS process whose classpath is exactly {@code zk-preview-launcher.jar} plus the
 * caller-supplied {@code --classpath} -- see IsolationChildProcessTest.
 */
public final class IsolatedRuntime {

    private static volatile Path extractedHooksJar;

    private IsolatedRuntime() {
    }

    public static ScopedZkClassLoader buildZkClassLoader(List<File> zkJars, ClassLoader parent,
                                                           ForbiddenLoadTracker tracker) {
        try {
            List<URL> urls = new ArrayList<>();
            for (File jar : zkJars) {
                urls.add(jar.toURI().toURL());
            }
            urls.add(hooksJarPath().toUri().toURL());
            return new ScopedZkClassLoader(urls.toArray(new URL[0]), parent, tracker);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build isolated ZK classloader", e);
        }
    }

    /** Extracts the isolation-hook classes (bundled as a main-jar resource) to a temp jar, once per JVM. */
    private static synchronized Path hooksJarPath() throws IOException {
        if (extractedHooksJar != null && Files.exists(extractedHooksJar)) {
            return extractedHooksJar;
        }
        try (InputStream in = IsolatedRuntime.class.getResourceAsStream("/zkpreview-hooks.jar")) {
            if (in == null) {
                throw new IOException("zkpreview-hooks.jar resource not found on the launcher's own classpath");
            }
            Path tmp = Files.createTempFile("zkpreview-hooks-", ".jar");
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            extractedHooksJar = tmp;
            return tmp;
        }
    }
}
