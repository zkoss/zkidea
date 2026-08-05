package org.zkoss.zkpreview;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.zkoss.zkpreview.jakarta.JakartaRenderEngine;
import org.zkoss.zkpreview.testutil.Variants;
import org.zkoss.zkpreview.testutil.ZkClasspathResolver;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AC-4 / E1-G3: isolation proofs. (iv) "hooks on -> (b)/(d) succeed" is covered by
 * {@link RenderFidelityTest} (same assertion, kept there to avoid duplication).
 * The rigorous end-to-end proof that the parent chain is genuinely narrow (no
 * process-wide classpath leak at all) is {@code IsolationChildProcessTest}, which
 * spawns the packaged CLI jar as a real, separate OS process.
 */
class IsolationTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");
    private static final List<String> CANARY_PREFIX = List.of("org.zkoss.zkpreview.testcanary.");

    static Stream<Variants.Named> variants() {
        return Variants.both();
    }

    @AfterEach
    void resetIsolationProperty() {
        System.clearProperty(IsolationMode.SYSTEM_PROPERTY);
    }

    // AC-4(i): the scoped classloader's own URL allowlist is exactly the resolved
    // ZK jars (+ the tiny injected hooks jar) -- never this module's own build
    // output (which is where a "user project's output dir" would show up if the
    // isolation boundary were broken).
    @ParameterizedTest(name = "[{0}]")
    @MethodSource("variants")
    void classpathAllowlistContainsOnlyZkJarsAndHooksJar(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        try (RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, null)) {
            ScopedZkClassLoader loader = zkLoaderOf(engine);
            URL[] urls = loader.getURLs();
            assertTrue(urls.length >= res.jars.size(), "expected at least the resolved ZK jars on the loader");

            Path moduleBuildDir = Paths.get(System.getProperty("zkpreview.moduleDir", "."), "build")
                    .toAbsolutePath().normalize();
            Path moduleTestClasses = moduleBuildDir.resolve("classes/java/test");

            for (URL u : urls) {
                Path p = Paths.get(u.toURI()).toAbsolutePath().normalize();
                assertFalse(p.startsWith(moduleTestClasses),
                        "render classpath must never include this module's own test output dir: " + p);
                boolean isResolvedZkJar = res.jars.stream()
                        .anyMatch(j -> j.getAbsoluteFile().toPath().normalize().equals(p));
                String fileName = p.getFileName().toString();
                boolean isHooksJar = fileName.startsWith("zkpreview-hooks-") && fileName.endsWith(".jar");
                assertTrue(isResolvedZkJar || isHooksJar,
                        "unexpected entry on the render classpath allowlist: " + p);
            }
        }
    }

    // AC-4(ii): parent loader identity. The scoped loader's parent is exactly the
    // classloader that defines the render engine's own glue code (never a broader,
    // caller-supplied classloader) -- see IsolatedRuntime's class-level javadoc for
    // why this must be so (classloader identity consistency for the mock objects).
    // In the real CLI deployment this classloader IS the platform-narrow system
    // classloader of a fresh JVM whose classpath is exactly the launcher jar --
    // proven end-to-end by IsolationChildProcessTest.
    @ParameterizedTest(name = "[{0}]")
    @MethodSource("variants")
    void parentLoaderIsTheLaunchersOwnDefiningLoaderNeverABroaderOne(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        try (RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, null)) {
            ScopedZkClassLoader loader = zkLoaderOf(engine);
            ClassLoader expectedParent = engine instanceof JakartaRenderEngine
                    ? JakartaRenderEngine.class.getClassLoader()
                    : org.zkoss.zkpreview.javax.JavaxRenderEngine.class.getClassLoader();
            assertSame(expectedParent, loader.getParent(),
                    "scoped loader's parent must be the launcher's own defining classloader");
            assertNotSame(ClassLoader.getSystemClassLoader().getParent(), loader.getParent(),
                    "sanity: parent must not accidentally be the bootstrap loader");
        }
    }

    // AC-4(iii): WITHOUT hooks, fixtures (b)/(d) fail, and the failure's cause chain
    // bottoms out in ClassNotFoundException naming the exact fixture FQCN. The
    // ForbiddenLoadTracker stands in for "this class is not on the render's
    // classpath at all" (true in the real, separate-process deployment; see
    // IsolationChildProcessTest for that end-to-end proof) so this fast in-process
    // test isn't defeated by canary classes being reachable via this test JVM's own
    // classpath, which fixture (b)/(d)'s classpath deliberately excludes in production.
    @ParameterizedTest(name = "[{0}]")
    @MethodSource("variants")
    void canaryModeWithoutHooksFailsWithClassNotFoundExceptionForViewModel(Variants.Named variant) throws Exception {
        System.setProperty(IsolationMode.SYSTEM_PROPERTY, "false");
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        ForbiddenLoadTracker tracker = new ForbiddenLoadTracker(CANARY_PREFIX);
        try (RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, tracker)) {
            RenderResult r = engine.renderZul("/viewmodel-bind.zul");
            assertFalse(r.isSuccess(), "expected FAILURE in canary mode (hooks off)");
            assertTrue(r.getError().getMessage().contains("org.zkoss.zkpreview.testcanary.CanaryViewModel"),
                    r.getError().getMessage());
            assertTrue(tracker.getAttempts().contains("org.zkoss.zkpreview.testcanary.CanaryViewModel"),
                    "expected a recorded load attempt for the exact fixture FQCN: " + tracker.getAttempts());
        }
    }

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("variants")
    void canaryModeWithoutHooksFailsWithClassNotFoundExceptionForComposer(Variants.Named variant) throws Exception {
        System.setProperty(IsolationMode.SYSTEM_PROPERTY, "false");
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        ForbiddenLoadTracker tracker = new ForbiddenLoadTracker(CANARY_PREFIX);
        try (RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, tracker)) {
            RenderResult r = engine.renderZul("/missing-composer.zul");
            assertFalse(r.isSuccess(), "expected FAILURE in canary mode (hooks off)");
            assertTrue(r.getError().getMessage().contains("org.zkoss.zkpreview.testcanary.CanaryComposer"),
                    r.getError().getMessage());
            assertTrue(tracker.getAttempts().contains("org.zkoss.zkpreview.testcanary.CanaryComposer"),
                    "expected a recorded load attempt for the exact fixture FQCN: " + tracker.getAttempts());
        }
    }

    private static ScopedZkClassLoader zkLoaderOf(RenderEngine engine) throws Exception {
        // The field lives on AbstractRenderEngine (shared base), so walk up from the concrete engine.
        for (Class<?> c = engine.getClass(); c != null; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField("zkLoader");
                f.setAccessible(true);
                return (ScopedZkClassLoader) f.get(engine);
            } catch (NoSuchFieldException ignored) {
                // try the superclass
            }
        }
        throw new NoSuchFieldException("zkLoader not found on " + engine.getClass() + " or its superclasses");
    }
}
