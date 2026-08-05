package org.zkoss.zkpreview;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R2-CRIT1 (code review #2): {@link ScopedZkClassLoader#loadClass} takes the child-first branch for
 * {@code org.zkoss.*} without holding {@code getClassLoadingLock(name)}, unlike the JDK's own
 * {@code ClassLoader.loadClass}. ZK loads component/util classes lazily on the request thread through
 * the single shared scoped loader, and {@link PreviewHttpServer} dispatches on a fixed pool of 8 --
 * one page load alone fans out into a burst of parallel {@code /zkau/web/*} GETs. Two threads that are
 * both first to touch the same not-yet-defined class both reach {@code defineClass}, and the JVM throws
 * {@code LinkageError: attempted duplicate class definition} at whichever one loses. That surfaces to
 * the developer as an intermittent render failure whose message reads like a duplicate ZK jar.
 *
 * <p>Reproduced here without any ZK jar: the launcher's own compiled classes are all under
 * {@code org.zkoss.zkpreview}, so they take the exact same child-first branch. Each class gets a fresh
 * loader and a barrier-synchronised first touch from every thread, repeated a few rounds, which puts
 * every thread inside the {@code findLoadedClass}-returns-null window simultaneously.
 *
 * <p>Note the {@code catch (ClassNotFoundException ignored)} in the loader does <em>not</em> absorb the
 * failure: {@code LinkageError} is an {@code Error} and propagates.
 */
class ScopedZkClassLoaderConcurrencyTest {

    /** Matches {@code PreviewHttpServer.HANDLER_THREADS} -- the real concurrency the loader sees. */
    private static final int THREADS = 8;
    private static final int ROUNDS_PER_CLASS = 4;

    @Test
    void concurrentFirstTouchOfTheSameZkClassNeverDuplicatesTheDefinition() throws Exception {
        Path classesDir = launcherClassesDir();
        Assumptions.assumeTrue(Files.isDirectory(classesDir),
                "skip: " + classesDir + " not built (run :zk-preview-launcher:classes first)");
        List<String> classNames = orgZkossClassNames(classesDir);
        Assumptions.assumeFalse(classNames.isEmpty(), "skip: no org.zkoss.* classes under " + classesDir);

        URL[] urls = {classesDir.toUri().toURL()};
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        try {
            for (String className : classNames) {
                for (int round = 0; round < ROUNDS_PER_CLASS; round++) {
                    try (ScopedZkClassLoader loader =
                                 new ScopedZkClassLoader(urls, getClass().getClassLoader(), null)) {
                        raceFirstTouch(pool, loader, className, failures);
                    }
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertTrue(failures.isEmpty(), () -> failures.size() + " of "
                + (classNames.size() * ROUNDS_PER_CLASS * THREADS) + " concurrent loadClass calls failed; "
                + "first: " + describe(failures.get(0)));
    }

    /** Releases {@link #THREADS} threads at once into {@code loadClass(className)} on a pristine loader. */
    private void raceFirstTouch(ExecutorService pool, ScopedZkClassLoader loader, String className,
                                List<Throwable> failures) throws Exception {
        CyclicBarrier startTogether = new CyclicBarrier(THREADS);
        List<Callable<Class<?>>> tasks = new ArrayList<>(THREADS);
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> {
                startTogether.await();
                return loader.loadClass(className);
            });
        }

        Set<Class<?>> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Future<Class<?>> future : pool.invokeAll(tasks)) {
            try {
                distinct.add(future.get());
            } catch (Exception e) {
                failures.add(e.getCause() != null ? e.getCause() : e);
            }
        }
        if (failures.isEmpty()) {
            assertEquals(1, distinct.size(),
                    "every thread must observe the same Class object for " + className);
        }
    }

    private static String describe(Throwable t) {
        return t.getClass().getName() + ": " + t.getMessage();
    }

    private static Path launcherClassesDir() {
        String moduleDir = System.getProperty("zkpreview.moduleDir", ".");
        return Path.of(moduleDir, "build", "classes", "java", "main").toAbsolutePath();
    }

    private static List<String> orgZkossClassNames(Path classesDir) throws IOException {
        Path root = classesDir.resolve("org").resolve("zkoss");
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> classesDir.relativize(p).toString()
                            .replace(java.io.File.separatorChar, '.')
                            .replaceAll("\\.class$", ""))
                    .sorted()
                    .toList();
        }
    }
}
