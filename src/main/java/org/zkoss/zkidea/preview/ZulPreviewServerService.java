package org.zkoss.zkidea.preview;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Project-level service that owns the {@code zk-preview-launcher} helper JVMs used by
 * the ZUL preview editor ({@link ZulPreviewFileEditor}).
 *
 * <p><b>Server lifetime policy</b> -- one helper JVM per distinct
 * {@code (docroot, classpath-signature)} pair, shared by every open preview tab that
 * resolves to that pair, and kept alive for the lifetime of the project session: closing
 * a preview tab only drops that tab's reference, it does not stop the server. This is
 * the simple choice recorded in tasks/zul-preview/PLAN.md's E3 deliverable -- it avoids
 * restart churn when switching between tabs of the same webapp, at the cost of one idle
 * JVM per previewed webapp/classpath combination until the project closes, at which
 * point {@link #dispose()} kills every server this service started (E3-G2: no orphan
 * JVMs left behind).
 */
public final class ZulPreviewServerService implements Disposable {

    private static final Logger LOG = Logger.getInstance(ZulPreviewServerService.class);
    private static final String LAUNCHER_JAR_NAME = "zk-preview-launcher.jar";
    private static final String PLUGIN_ID = "org.zkoss.zkidea";

    private final Project project;
    private final Map<String, ManagedPreviewServer> serversByKey = new ConcurrentHashMap<>();

    public ZulPreviewServerService(Project project) {
        this.project = project;
    }

    public static ZulPreviewServerService getInstance(@NotNull Project project) {
        return project.getService(ZulPreviewServerService.class);
    }

    /**
     * Resolves the preview target for {@code zulFile} off the EDT (inside a read
     * action, per RESEARCH.md U5-F5/F6), ensures a helper JVM backs it, and delivers
     * the outcome on the EDT via {@code onReady}.
     */
    public void preparePreview(@NotNull VirtualFile zulFile, @NotNull Consumer<PreviewResult> onReady) {
        ReadAction.nonBlocking(() -> resolveTarget(zulFile))
                .expireWith(this)
                .finishOnUiThread(ModalityState.any(), target -> onTargetResolved(target, onReady))
                .submit(AppExecutorUtil.getAppExecutorService());
    }

    private void onTargetResolved(PreviewTarget target, Consumer<PreviewResult> onReady) {
        if (target.libraryJars.isEmpty()) {
            onReady.accept(PreviewResult.noZkJars());
            return;
        }
        String key = target.docroot + "#" + target.classpathSignature;
        ManagedPreviewServer server = serversByKey.compute(key, (k, existing) ->
                (existing != null && existing.isAlive()) ? existing : startServer(target));
        deliver(server, target.requestPath, onReady);
    }

    private void deliver(ManagedPreviewServer server, String requestPath, Consumer<PreviewResult> onReady) {
        server.portFuture().whenComplete((port, ex) -> ApplicationManager.getApplication().invokeLater(() -> {
            if (ex != null) {
                LOG.warn("ZUL preview server failed to start", ex);
                onReady.accept(PreviewResult.error(rootMessage(ex)));
            } else {
                onReady.accept(PreviewResult.ready(port, requestPath));
            }
        }));
    }

    private ManagedPreviewServer startServer(PreviewTarget target) {
        GeneralCommandLine commandLine = new GeneralCommandLine()
                .withExePath(resolveJavaExecutable())
                .withParameters("-jar", resolveLauncherJar().toString(),
                        "--classpath", joinClasspath(target.launcherClasspath),
                        "--webapp", target.docroot.toString(),
                        "--port", "0",
                        // Identify this plugin/IDE in the error page's "Report on GitHub"
                        // link (the launcher can't know them); OS/JDK it fills in itself.
                        "--report-plugin", "ZKIdea " + PreviewIssueReporter.pluginVersion(),
                        "--report-ide", PreviewIssueReporter.ideDescription());
        try {
            ManagedPreviewServer server = new ManagedPreviewServer(commandLine);
            server.start();
            return server;
        } catch (ExecutionException e) {
            return ManagedPreviewServer.failed(e);
        }
    }

    /**
     * Detects the module's ZK jars and the docroot for {@code zulFile}. Must run inside
     * a read action -- it touches {@link ProjectFileIndex}/{@link OrderEnumerator}.
     */
    private PreviewTarget resolveTarget(VirtualFile zulFile) {
        Module module = ProjectFileIndex.getInstance(project).getModuleForFile(zulFile);
        // .withoutSdk() (D4, PLAN.md E3 round 3): without it, a live launcher process
        // was observed with project-SDK pseudo-entries on its classpath (e.g.
        // ".../zulu-24.jdk/Contents/Home!/java.base", a JDK module root) -- the SDK is
        // never something ZK needs handed explicitly, and filterLibraryJars' defensive
        // "existing regular file only" check is a second line of defense, not the fix.
        List<String> classpathEntries = (module != null)
                ? OrderEnumerator.orderEntries(module).recursively().runtimeOnly().withoutSdk()
                        .classes().getPathsList().getPathList()
                : OrderEnumerator.orderEntries(project).runtimeOnly().withoutSdk()
                        .classes().getPathsList().getPathList();

        // Presence check only (R7 "no ZK jars" gate) -- the actual handoff classpath
        // below is deliberately wider (see filterLibraryJars' javadoc / PLAN.md D1):
        // ZK's own runtime deps (e.g. slf4j-api) are not ZK-prefixed, so a ZK-only
        // allowlist starves the launcher's bootstrap.
        boolean hasZkJars = !ZkClasspathFilter.filterZkJars(classpathEntries).isEmpty();
        List<File> libraryJars = hasZkJars
                ? ZkClasspathFilter.filterLibraryJars(classpathEntries)
                : List.of();

        // Resource roots (e.g. src/main/resources): where a user's own ~./ ClassWebResource
        // pages (web/*.zul) live. ZK resolves ~./x from the classpath at /web/x, so these
        // directories must be on the launcher's classpath for a user's ~./ page to render --
        // it works in a real container because WEB-INF/classes/web/ is on the classpath there.
        // This is NOT the module *output* dir (AC-4(i)/filterLibraryJars still exclude that):
        // a resource root holds resources, not compiled user classes, so isolation (the
        // UiFactory hook) is unaffected. Only meaningful when the module has ZK at all.
        List<File> resourceRoots = (module != null && hasZkJars)
                ? ZkClasspathFilter.filterResourceRoots(
                        ModuleRootManager.getInstance(module)
                                .getSourceRoots(JavaResourceRootType.RESOURCE).stream()
                                .map(VirtualFile::getPath)
                                .collect(Collectors.toList()))
                : List.of();

        // Jars first so ZK's own bundled web/ resources win over any user name collision.
        List<File> launcherClasspath = new ArrayList<>(libraryJars);
        launcherClasspath.addAll(resourceRoots);
        String signature = ZkClasspathFilter.signature(launcherClasspath);

        List<Path> contentRoots = module == null ? List.of()
                : Arrays.stream(ModuleRootManager.getInstance(module).getContentRoots())
                        .map(vf -> Paths.get(vf.getPath()))
                        .collect(Collectors.toList());

        Path zulPath = Paths.get(zulFile.getPath());
        Path docroot = DocrootResolver.resolve(zulPath, contentRoots);
        String relative = docroot.relativize(zulPath).toString().replace(File.separatorChar, '/');
        return new PreviewTarget(docroot, libraryJars, launcherClasspath, signature, "/" + relative);
    }

    private String resolveJavaExecutable() {
        Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
        if (sdk != null && sdk.getSdkType() instanceof JavaSdkType) {
            String exe = ((JavaSdkType) sdk.getSdkType()).getVMExecutablePath(sdk);
            if (exe != null) {
                return exe;
            }
        }
        // Fallback: the JRE running the IDE itself (RESEARCH.md U5).
        return Paths.get(System.getProperty("java.home"), "bin", SystemInfo.isWindows ? "java.exe" : "java")
                .toString();
    }

    private Path resolveLauncherJar() {
        IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID));
        if (descriptor == null) {
            throw new IllegalStateException("Could not locate the '" + PLUGIN_ID + "' plugin descriptor");
        }
        return descriptor.getPluginPath().resolve("lib").resolve(LAUNCHER_JAR_NAME);
    }

    private static String joinClasspath(List<File> jars) {
        return jars.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator));
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }

    @Override
    public void dispose() {
        for (ManagedPreviewServer server : serversByKey.values()) {
            server.destroy();
        }
        serversByKey.clear();
    }

    /** Immutable resolution result for one preview request. */
    private static final class PreviewTarget {
        final Path docroot;
        /** Runtime library jars only (never directories); empty iff no ZK jars were found -- the "has ZK?" gate. */
        final List<File> libraryJars;
        /** What the launcher actually gets on {@code --classpath}: {@link #libraryJars} plus resource-root dirs. */
        final List<File> launcherClasspath;
        final String classpathSignature;
        final String requestPath;

        PreviewTarget(Path docroot, List<File> libraryJars, List<File> launcherClasspath,
                      String classpathSignature, String requestPath) {
            this.docroot = docroot;
            this.libraryJars = libraryJars;
            this.launcherClasspath = launcherClasspath;
            this.classpathSignature = classpathSignature;
            this.requestPath = requestPath;
        }
    }
}
