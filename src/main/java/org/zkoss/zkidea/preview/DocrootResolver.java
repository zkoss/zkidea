package org.zkoss.zkidea.preview;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Resolves the "docroot" (webapp root) a previewed {@code .zul} file must be served
 * relative to, so the {@code --webapp} argument handed to the {@code zk-preview-launcher}
 * helper JVM matches the layout ZK's own {@code DHtmlLayoutServlet} expects.
 *
 * <p>Rule, walked from the {@code .zul} file's parent directory upward:
 * <ol>
 *   <li>The first ancestor directory that either contains a {@code WEB-INF}
 *       subdirectory, or is itself named {@code webapp} (case-insensitive), is the
 *       docroot. This matches the standard Maven/Gradle webapp layout
 *       ({@code src/main/webapp/WEB-INF/...}).</li>
 *   <li>If no such ancestor is found, fall back to the nearest of {@code boundaryRoots}
 *       that is an ancestor of the file (typically a module's source/content root).</li>
 *   <li>If none of the boundary roots contains the file either, fall back to the
 *       file's own parent directory.</li>
 * </ol>
 */
public final class DocrootResolver {

    private DocrootResolver() {
    }

    public static Path resolve(Path zulFile, List<Path> boundaryRoots) {
        Path parent = zulFile.getParent();
        if (parent == null) {
            return zulFile;
        }
        for (Path candidate = parent; candidate != null; candidate = candidate.getParent()) {
            if (hasWebInf(candidate) || isNamedWebapp(candidate)) {
                return candidate;
            }
        }
        for (Path root : boundaryRoots) {
            if (parent.startsWith(root)) {
                return root;
            }
        }
        return parent;
    }

    private static boolean hasWebInf(Path dir) {
        return Files.isDirectory(dir.resolve("WEB-INF"));
    }

    private static boolean isNamedWebapp(Path dir) {
        Path name = dir.getFileName();
        return name != null && "webapp".equalsIgnoreCase(name.toString());
    }
}
