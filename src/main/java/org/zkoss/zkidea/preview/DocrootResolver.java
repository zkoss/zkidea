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
 *   <li>The first ancestor directory -- searched only within {@code boundaryRoots}, so an
 *       unrelated ancestor named {@code webapp} above the module cannot hijack it -- that either
 *       contains a {@code WEB-INF} subdirectory, or is itself named {@code webapp}
 *       (case-insensitive), is the docroot. This matches the standard Maven/Gradle webapp (WAR)
 *       layout ({@code src/main/webapp/WEB-INF/...}).</li>
 *   <li>Otherwise, the first ancestor that is a ZK <em>classpath web root</em> -- a
 *       directory named {@code web} directly under one of {@code resourceRoots}
 *       ({@code src/main/resources/web}) -- is the docroot. This is the Spring-Boot-jar
 *       layout: pages live on the classpath ({@code ~./...}), with no {@code webapp}/{@code WEB-INF},
 *       and must be served at their production url ({@code /index.zul}, not
 *       {@code /src/main/resources/web/index.zul}).</li>
 *   <li>If neither matches, fall back to the nearest of {@code boundaryRoots}
 *       that is an ancestor of the file (typically a module's source/content root).</li>
 *   <li>If none of the boundary roots contains the file either, fall back to the
 *       file's own parent directory.</li>
 * </ol>
 */
public final class DocrootResolver {

    /**
     * Which of the rules above produced a docroot. Reported in a preview failure's GitHub issue
     * (doc/zul_preview_spec.md §2.7): the branch taken explains most
     * "page not found" / broken-{@code <include>} / {@code ~./}-not-resolving reports, and a
     * fallback branch is itself a strong hint that the project layout wasn't recognised.
     */
    public enum Layout {
        WAR_WEBAPP("WAR webapp"),
        SPRING_BOOT_CLASSPATH("Spring Boot classpath web"),
        CONTENT_ROOT("content-root fallback"),
        FILE_PARENT("file-parent fallback");

        private final String label;

        Layout(String label) {
            this.label = label;
        }

        /** Human-readable form for a bug report. */
        public String getLabel() {
            return label;
        }
    }

    /** A resolved docroot together with the rule that produced it. */
    public static final class Resolution {
        private final Path docroot;
        private final Layout layout;

        Resolution(Path docroot, Layout layout) {
            this.docroot = docroot;
            this.layout = layout;
        }

        public Path getDocroot() {
            return docroot;
        }

        public Layout getLayout() {
            return layout;
        }
    }

    private DocrootResolver() {
    }

    public static Path resolve(Path zulFile, List<Path> boundaryRoots) {
        return resolve(zulFile, boundaryRoots, List.of());
    }

    public static Path resolve(Path zulFile, List<Path> boundaryRoots, List<Path> resourceRoots) {
        return resolveWithLayout(zulFile, boundaryRoots, resourceRoots).getDocroot();
    }

    /**
     * As {@link #resolve}, but also reports <em>which</em> rule matched. The layout is captured
     * here rather than re-derived from the returned path so it can never drift from the branch
     * actually taken.
     */
    public static Resolution resolveWithLayout(Path zulFile, List<Path> boundaryRoots, List<Path> resourceRoots) {
        Path parent = zulFile.getParent();
        if (parent == null) {
            return new Resolution(zulFile, Layout.FILE_PARENT);
        }
        for (Path candidate = parent; candidate != null && withinBoundary(candidate, boundaryRoots);
                candidate = candidate.getParent()) {
            if (hasWebInf(candidate) || isNamedWebapp(candidate)) {
                return new Resolution(candidate, Layout.WAR_WEBAPP);
            }
        }
        for (Path candidate = parent; candidate != null; candidate = candidate.getParent()) {
            if (isClasspathWebRoot(candidate, resourceRoots)) {
                return new Resolution(candidate, Layout.SPRING_BOOT_CLASSPATH);
            }
        }
        for (Path root : boundaryRoots) {
            if (parent.startsWith(root)) {
                return new Resolution(root, Layout.CONTENT_ROOT);
            }
        }
        return new Resolution(parent, Layout.FILE_PARENT);
    }

    private static boolean hasWebInf(Path dir) {
        return Files.isDirectory(dir.resolve("WEB-INF"));
    }

    private static boolean isNamedWebapp(Path dir) {
        Path name = dir.getFileName();
        return name != null && "webapp".equalsIgnoreCase(name.toString());
    }

    /**
     * ZK's classpath web-resource root: a directory named {@code web} sitting directly under a
     * module resource root (e.g. {@code src/main/resources/web}). This is the Spring-Boot-jar
     * layout -- pages served from the classpath ({@code ~./}) with no {@code webapp}/{@code WEB-INF}.
     * Gated on "parent is a known resource root" so an unrelated {@code web} directory is not
     * mistaken for it. {@code web} is a fixed ZK convention (ClassWebResource {@code /web}), so
     * the name match is exact, not case-insensitive.
     */
    /**
     * A candidate ancestor is in-bounds for the WEB-INF/{@code webapp} scan iff no boundary roots
     * were supplied (unbounded -- back-compat for the 2-arg overload / callers that pass none) or it
     * lies within one. Stops the scan escaping the module and mistaking an unrelated ancestor named
     * {@code webapp} (e.g. a {@code ~/webapp/...} checkout folder) for the docroot.
     */
    private static boolean withinBoundary(Path dir, List<Path> boundaryRoots) {
        if (boundaryRoots.isEmpty()) {
            return true;
        }
        for (Path root : boundaryRoots) {
            if (dir.startsWith(root)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isClasspathWebRoot(Path dir, List<Path> resourceRoots) {
        Path name = dir.getFileName();
        if (name == null || !"web".equals(name.toString())) {
            return false;
        }
        Path parent = dir.getParent();
        return parent != null && resourceRoots.contains(parent);
    }
}
