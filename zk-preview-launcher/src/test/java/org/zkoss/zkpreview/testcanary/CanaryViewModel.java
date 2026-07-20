package org.zkoss.zkpreview.testcanary;

/**
 * Compiled as a normal test class (present on disk, on the test JVM's own
 * classpath) but never on the classpath list a render's {@code ScopedZkClassLoader}
 * is built from -- the AC-4 negative control for the {@code viewModel=} path.
 * If isolation ever regresses, this class would actually get constructed and its
 * value would leak into the rendered HTML.
 */
public class CanaryViewModel {
    public static volatile boolean WAS_CONSTRUCTED = false;

    public CanaryViewModel() {
        WAS_CONSTRUCTED = true;
    }

    public String getGreeting() {
        return "LOADED-CANARY-VALUE";
    }

    public void doIt() {
    }
}
