package org.zkoss.zkpreview;

import org.zkoss.zkpreview.jakarta.JakartaRenderEngine;
import org.zkoss.zkpreview.javax.JavaxRenderEngine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Programmatic entry point: detects which servlet API a ZK classpath needs -- ZK 9.x and
 * earlier are {@code javax.servlet}, ZK 10+ is {@code jakarta.servlet}; both variants are
 * supported and the choice is always derived from the resolved ZK jars, never configured --
 * and builds the matching {@link RenderEngine}. This is the
 * "independently callable" rendering core -- no IntelliJ APIs, callable directly
 * from a JUnit test or embedded elsewhere, in addition to the CLI ({@link Main}).
 */
public final class RenderEngineFactory {

    private RenderEngineFactory() {
    }

    public static RenderEngine create(List<File> zkJars, Path webappDir) throws IOException {
        return create(zkJars, webappDir, null);
    }

    public static RenderEngine create(List<File> zkJars, Path webappDir, ForbiddenLoadTracker forbiddenLoadTracker)
            throws IOException {
        return create(zkJars, webappDir, forbiddenLoadTracker, ControllerPolicy.fromProcessDefault());
    }

    /**
     * As above, with an explicit {@link ControllerPolicy} -- what {@link Main} builds from
     * {@code --isolation}/{@code --controller-timeout} (P0-2). The shorter overloads keep
     * defaulting to isolated so every existing caller, and the IntelliJ plugin, is unaffected.
     */
    public static RenderEngine create(List<File> zkJars, Path webappDir, ForbiddenLoadTracker forbiddenLoadTracker,
            ControllerPolicy controllerPolicy) throws IOException {
        ZkVariant variant = VariantDetector.detect(zkJars);
        if (variant == ZkVariant.JAKARTA) {
            return new JakartaRenderEngine(zkJars, webappDir, forbiddenLoadTracker, controllerPolicy);
        }
        return new JavaxRenderEngine(zkJars, webappDir, forbiddenLoadTracker, controllerPolicy);
    }
}
