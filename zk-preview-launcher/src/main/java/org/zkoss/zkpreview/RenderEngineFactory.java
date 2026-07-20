package org.zkoss.zkpreview;

import org.zkoss.zkpreview.jakarta.JakartaRenderEngine;
import org.zkoss.zkpreview.javax.JavaxRenderEngine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Programmatic entry point: detects which servlet API a ZK classpath needs
 * (RESEARCH.md R2) and builds the matching {@link RenderEngine}. This is the
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
        ZkVariant variant = VariantDetector.detect(zkJars);
        if (variant == ZkVariant.JAKARTA) {
            return new JakartaRenderEngine(zkJars, webappDir, forbiddenLoadTracker);
        }
        return new JavaxRenderEngine(zkJars, webappDir, forbiddenLoadTracker);
    }
}
