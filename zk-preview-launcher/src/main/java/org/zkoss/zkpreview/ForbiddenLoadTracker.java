package org.zkoss.zkpreview;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records every class-load attempt a {@link ScopedZkClassLoader} makes for a name
 * under a caller-supplied forbidden prefix (e.g. the project package of a test
 * "canary" class). Used by the AC-4 isolation tests as a second, independent proof
 * alongside "the class genuinely could not be found".
 */
public final class ForbiddenLoadTracker {

    private final List<String> attempts = new CopyOnWriteArrayList<>();
    private final List<String> forbiddenPrefixes;

    public ForbiddenLoadTracker(List<String> forbiddenPrefixes) {
        this.forbiddenPrefixes = List.copyOf(forbiddenPrefixes);
    }

    boolean isForbidden(String className) {
        for (String prefix : forbiddenPrefixes) {
            if (className.startsWith(prefix)) return true;
        }
        return false;
    }

    void recordAttempt(String className) {
        attempts.add(className);
    }

    public List<String> getAttempts() {
        return List.copyOf(attempts);
    }
}
