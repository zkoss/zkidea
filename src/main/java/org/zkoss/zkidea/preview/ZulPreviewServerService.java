package org.zkoss.zkidea.preview;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Project-level service that owns the {@code zk-preview-launcher} helper JVMs used by
 * the ZUL preview editor ({@link ZulPreviewFileEditor}).
 *
 * <p><b>Server lifetime policy</b> -- one helper JVM per distinct
 * {@code (docroot, classpath-signature)} pair, shared by every open preview tab that
 * resolves to that pair, and kept alive for the lifetime of the project session: closing
 * a preview tab only drops that tab's reference, it does not stop the server. The trade
 * is deliberate: it avoids restart churn when switching between tabs of the same webapp,
 * at the cost of one idle JVM per previewed webapp/classpath combination until the project
 * closes, at which point {@link #dispose()} kills every server this service started, so no
 * orphan JVMs are left behind (locked by {@code ManagedPreviewServerTeardownTest}).
 */
public final class ZulPreviewServerService implements Disposable {

    private static final Logger LOG = Logger.getInstance(ZulPreviewServerService.class);
    private static final String LAUNCHER_JAR_NAME = "zk-preview-launcher.jar";
    private static final String PLUGIN_ID = "org.zkoss.zkidea";
    /**
     * The oldest JVM that can load {@link #LAUNCHER_JAR_NAME}. Must track the launcher module's
     * {@code targetCompatibility} -- {@code LauncherJvmVersionGateTest} reads the packaged jar's
     * actual bytecode level and fails if the two drift apart.
     */
    static final JavaSdkVersion MINIMUM_LAUNCHER_SDK = JavaSdkVersion.JDK_17;

    private final Project project;
    private final Map<String, ManagedPreviewServer> serversByKey = new ConcurrentHashMap<>();

    public ZulPreviewServerService(Project project) {
        this.project = project;
    }

    public static ZulPreviewServerService getInstance(@NotNull Project project) {
        return project.getService(ZulPreviewServerService.class);
    }

    /**
     * Resolves the preview target for {@code zulFile} off the EDT, ensures a helper JVM
     * backs it, and delivers the outcome on the EDT via {@code onReady}.
     *
     * <p>The resolve must run inside a read action -- it touches the project model
     * ({@link ProjectFileIndex}/{@link OrderEnumerator}) -- but deliberately <em>not</em>
     * inside {@code ReadAction.compute()}: a long, non-cancellable read action on a
     * background thread can block write actions and freeze the UI, which is why the
     * platform steers this exact case to {@code ReadAction.nonBlocking(...).submit(...)}.
     */
    public void preparePreview(@NotNull VirtualFile zulFile, @NotNull Consumer<PreviewResult> onReady) {
        // Resolve the target AND ensure its helper JVM entirely off the EDT (U1): constructing the
        // process (KillableProcessHandler's fork/exec) is synchronous and must not freeze the IDE on
        // the feature's most common path -- opening the first .zul in a module. onTargetResolved runs
        // on the background executor and marshals onReady back to the EDT (the caller drives Swing/JCEF).
        wireResolveOutcome(
                ReadAction.nonBlocking(() -> resolveTarget(zulFile))
                        .expireWith(this)
                        .submit(AppExecutorUtil.getAppExecutorService()),
                target -> onTargetResolved(target, onReady),
                ex -> deliverResult(PreviewResult.error(rootMessage(ex)), onReady));
    }

    /**
     * Wires BOTH outcomes of the target-resolution promise (review R2-CRIT3). Only {@code onSuccess}
     * used to be attached, so a throw from {@link #resolveTarget} rejected the promise silently,
     * {@code onReady} never fired, and the pane stayed on "Starting ZK preview server…" forever with
     * no message, no Report link and no retry -- the same U2 "stuck loading" dead end {@link
     * #startGuarded} closes one step later. Package-visible and platform-free (it touches only the
     * promise and the two supplied consumers) so the wiring is unit-testable without an {@code
     * Application}, exactly like the {@code startGuarded} seam.
     *
     * <p>Cancellation needs no special case here: the promise is expired with this project-level
     * service, so it can only be cancelled at project close, by which point the editor is disposed
     * and its {@code onReady} callback is already a no-op.
     */
    static <T> void wireResolveOutcome(@NotNull Promise<T> resolution,
                                       @NotNull Consumer<? super T> onResolved,
                                       @NotNull Consumer<? super Throwable> onFailed) {
        resolution.onSuccess(onResolved::accept).onError(onFailed::accept);
    }

    private void onTargetResolved(PreviewTarget target, Consumer<PreviewResult> onReady) {
        // Runs on the background executor (U1). Every early return marshals onReady to the EDT.
        // Every outcome carries the render-target environment: the "no ZK"/"stale classpath" cards
        // offer a GitHub report too, and the build tool + resolved jar list are exactly what makes
        // those reports actionable.
        String environment = target.reportEnvironment();
        switch (target.presence) {
            case NONE:
                deliverResult(PreviewResult.noZkJars().withEnvironment(environment), onReady);
                return;
            case DECLARED_BUT_MISSING:
                deliverResult(PreviewResult.staleClasspath().withEnvironment(environment), onReady);
                return;
            default:
                break;
        }
        String key = target.docroot + "#" + target.classpathSignature;
        ManagedPreviewServer server = serversByKey.compute(key, (k, existing) ->
                (existing != null && existing.isAlive()) ? existing : startServer(target));
        deliver(server, target.requestPath, environment, onReady);
    }

    private void deliverResult(PreviewResult result, Consumer<PreviewResult> onReady) {
        ApplicationManager.getApplication().invokeLater(() -> onReady.accept(result));
    }

    private void deliver(ManagedPreviewServer server, String requestPath, String environment,
                         Consumer<PreviewResult> onReady) {
        server.portFuture().whenComplete((port, ex) -> ApplicationManager.getApplication().invokeLater(() -> {
            if (ex != null) {
                LOG.warn("ZUL preview server failed to start", ex);
                onReady.accept(PreviewResult.error(rootMessage(ex)).withEnvironment(environment));
            } else {
                onReady.accept(PreviewResult.ready(port, requestPath).withEnvironment(environment));
            }
        }));
    }

    private ManagedPreviewServer startServer(PreviewTarget target) {
        // Build the command line INSIDE the guarded supplier: resolveLauncherJar() throws when the
        // plugin installation directory can't be located, and resolveJavaExecutable() also touches
        // platform lookups. Any throw here must become a failed server (surfaced as an error+Report
        // card), never an escape that leaves the pane stuck on "loading" forever (U2).
        return startGuarded(() -> {
            List<String> parameters = new ArrayList<>(List.of("-jar", resolveLauncherJar().toString(),
                    "--classpath", joinClasspath(target.launcherClasspath),
                    "--webapp", target.docroot.toString(),
                    "--port", "0"));
            parameters.addAll(reportArguments("ZKIdea " + PreviewIssueReporter.pluginVersion(),
                    PreviewIssueReporter.ideDescription(),
                    target.buildSystem, target.layout, target.zkJarSummary));
            return new GeneralCommandLine()
                    .withExePath(resolveJavaExecutable())
                    .withParameters(parameters);
        });
    }

    /**
     * The facts the render-error page's "Report on GitHub" link needs but the launcher cannot know:
     * our identity, plus the render target exactly as IntelliJ resolved it. OS/JDK and the detected
     * servlet variant the launcher fills in itself.
     *
     * <p>Package-visible and platform-free so {@code ReportArgumentsTest} can lock the flag names:
     * they are matched by string key in {@code org.zkoss.zkpreview.Main#reportEnv}, across a module
     * boundary with no shared constant, and a silent rename would drop facts from every report.
     */
    static List<String> reportArguments(String plugin, String ide, String buildSystem, String layout,
                                        String zkJarSummary) {
        return List.of(
                "--report-plugin", plugin,
                "--report-ide", ide,
                "--report-build", buildSystem,
                "--report-layout", layout,
                "--report-zkjars", zkJarSummary);
    }

    /**
     * Builds and starts a helper server, converting ANY failure -- a throw from {@code
     * commandLineSupplier} (e.g. {@link #resolveLauncherJar()} when our own jar can't be located) or
     * an {@link ExecutionException} from process creation -- into a "failed" server whose {@code
     * portFuture} completes exceptionally. This guarantees {@link #deliver} always reaches {@code
     * onReady} with an error/Report outcome instead of the escape that used to leave the pane stuck on
     * "loading" (U2). Package-visible and platform-free so it can be unit-tested with any supplier.
     */
    static ManagedPreviewServer startGuarded(Supplier<GeneralCommandLine> commandLineSupplier) {
        try {
            ManagedPreviewServer server = new ManagedPreviewServer(commandLineSupplier.get());
            server.start();
            return server;
        } catch (ExecutionException | RuntimeException e) {
            return ManagedPreviewServer.failed(e);
        }
    }

    /**
     * Detects the module's ZK jars and the docroot for {@code zulFile}. Must run inside
     * a read action -- it touches {@link ProjectFileIndex}/{@link OrderEnumerator}.
     */
    private PreviewTarget resolveTarget(VirtualFile zulFile) {
        Module module = ProjectFileIndex.getInstance(project).getModuleForFile(zulFile);
        // .withoutSdk(): without it, a live launcher process spawned by the real IDE
        // was observed with project-SDK pseudo-entries on its classpath (e.g.
        // ".../zulu-24.jdk/Contents/Home!/java.base", a JDK module root) -- the SDK is
        // never something ZK needs handed explicitly, and filterLibraryJars' defensive
        // "existing regular file only" check is a second line of defense, not the fix.
        List<String> classpathEntries = (module != null)
                ? OrderEnumerator.orderEntries(module).recursively().runtimeOnly().withoutSdk()
                        .classes().getPathsList().getPathList()
                : OrderEnumerator.orderEntries(project).runtimeOnly().withoutSdk()
                        .classes().getPathsList().getPathList();

        // What actually resolved, summarised for a GitHub failure report. Computed over the RAW
        // entries (not the filtered launcher classpath below) so a declared-but-missing ZK jar
        // still shows up -- that is exactly the DECLARED_BUT_MISSING case a reporter needs to see.
        String zkJarSummary = ZkClasspathFilter.classpathSummary(classpathEntries);
        String buildSystem = BuildSystemDetector.detect(module);

        // The same enumeration again, production-only: this one supplies the compiled-output
        // roots (target/classes, ...) the launcher needs to resolve the project's own classes.
        // Enumerated separately rather than reusing the list above, because the two views must
        // differ in both directions: productionOnly() drops provided-scope jars, which the
        // handoff still needs, and the wider view carries test output roots, which it must not
        // have (see ZkClasspathFilter.filterOutputDirectories).
        List<String> productionClassEntries = (module != null)
                ? OrderEnumerator.orderEntries(module).recursively().runtimeOnly().productionOnly()
                        .withoutSdk().classes().getPathsList().getPathList()
                : OrderEnumerator.orderEntries(project).runtimeOnly().productionOnly()
                        .withoutSdk().classes().getPathsList().getPathList();

        // The module's RESOURCE source roots (e.g. src/main/resources), computed once and used
        // for two things: (a) the launcher classpath below (where a user's own ~./
        // ClassWebResource pages live -- ZK resolves ~./x from the classpath at /web/x), and
        // (b) docroot resolution -- a Spring-Boot-jar page under <resourceRoot>/web has no
        // webapp/WEB-INF, so DocrootResolver needs these roots to recognise its classpath web root.
        List<String> resourceRootPaths = (module != null)
                ? ModuleRootManager.getInstance(module)
                        .getSourceRoots(JavaResourceRootType.RESOURCE).stream()
                        .map(VirtualFile::getPath)
                        .collect(Collectors.toList())
                : List.of();

        // ZK presence gate, three-way: NONE ("add a ZK dependency"), DECLARED_BUT_MISSING
        // (declared but not on disk -> "re-import/re-sync"), or PRESENT.
        // Only PRESENT proceeds to a launcher. The actual handoff classpath is deliberately
        // wider than just ZK jars (see filterLibraryJars' javadoc): ZK's own runtime deps (e.g.
        // slf4j-api) are not ZK-prefixed, so a ZK-only allowlist starves the bootstrap.
        ZkClasspathFilter.ZkPresence presence = ZkClasspathFilter.detectZkPresence(classpathEntries);
        boolean hasZkJars = presence == ZkClasspathFilter.ZkPresence.PRESENT;
        List<File> launcherClasspath = hasZkJars
                ? launcherClasspath(classpathEntries, productionClassEntries, resourceRootPaths)
                : List.of();
        String signature = ZkClasspathFilter.signature(launcherClasspath);

        List<Path> contentRoots = module == null ? List.of()
                : Arrays.stream(ModuleRootManager.getInstance(module).getContentRoots())
                        .map(vf -> Paths.get(vf.getPath()))
                        .collect(Collectors.toList());

        Path zulPath = Paths.get(zulFile.getPath());
        List<Path> resourceRootDirs = resourceRootPaths.stream().map(Paths::get).collect(Collectors.toList());
        DocrootResolver.Resolution resolution =
                DocrootResolver.resolveWithLayout(zulPath, contentRoots, resourceRootDirs);
        Path docroot = resolution.getDocroot();
        String relative = docroot.relativize(zulPath).toString().replace(File.separatorChar, '/');
        return new PreviewTarget(presence, docroot, launcherClasspath, signature, "/" + relative,
                buildSystem, resolution.getLayout().getLabel(), zkJarSummary);
    }

    /**
     * What the helper JVM gets on {@code --classpath}, in this order: every resolved runtime
     * <b>library jar</b>, then the <b>compiled-output roots</b> of the previewed module and its
     * module dependencies, then the module's <b>resource roots</b>.
     *
     * <p>Order is the contract. Jars first so ZK's own bundled {@code web/} resources win over a
     * user name collision; compiled output before the resource roots, mirroring a real container
     * where {@code WEB-INF/classes} <em>is</em> the compiled output with the resources already
     * copied into it.
     *
     * <p>{@code classpathEntries} is the full runtime enumeration (keeps provided-scope jars);
     * {@code productionClassEntries} is the production-only one and is the only source of
     * directories, so {@code target/test-classes} never reaches the render. The two lists cannot
     * produce duplicates: only files are taken from the first, only directories from the second.
     *
     * <p>Package-visible, static and platform-free so {@code LauncherClasspathTest} and the
     * plugin&lt;-&gt;launcher {@code ZulPreviewLauncherSeamTest} can build the real thing without
     * an {@code Application}.
     */
    static List<File> launcherClasspath(List<String> classpathEntries,
                                        List<String> productionClassEntries,
                                        List<String> resourceRootPaths) {
        List<File> classpath = new ArrayList<>(ZkClasspathFilter.filterLibraryJars(classpathEntries));
        classpath.addAll(ZkClasspathFilter.filterOutputDirectories(productionClassEntries));
        classpath.addAll(ZkClasspathFilter.filterResourceRoots(resourceRootPaths));
        return classpath;
    }

    private String resolveJavaExecutable() {
        Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
        if (sdk != null && sdk.getSdkType() instanceof JavaSdkType) {
            JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
            if (canRunLauncherJar(version)) {
                String exe = ((JavaSdkType) sdk.getSdkType()).getVMExecutablePath(sdk);
                if (exe != null) {
                    return exe;
                }
            } else {
                LOG.info("Project SDK '" + sdk.getName() + "' (" + version + ") is older than "
                        + MINIMUM_LAUNCHER_SDK + " and cannot load " + LAUNCHER_JAR_NAME
                        + "; running the preview helper on the IDE runtime instead");
            }
        }
        // Fallback: the JRE running the IDE itself. There is no JetBrains guidance prescribing
        // which JDK a plugin should launch a helper JVM from; the corroborated precedent is the
        // project SDK via ProjectRootManager.getProjectSdk() + JavaSdkType.getVMExecutablePath()
        // (above), with the IDE's own runtime as the fallback so a project with no configured
        // -- or too old (see canRunLauncherJar) -- SDK still previews.
        return Paths.get(System.getProperty("java.home"), "bin", SystemInfo.isWindows ? "java.exe" : "java")
                .toString();
    }

    /**
     * Whether a project SDK of {@code version} can load {@link #LAUNCHER_JAR_NAME}'s main class.
     *
     * <p>The launcher jar is compiled to {@link #MINIMUM_LAUNCHER_SDK} bytecode, so an older project
     * SDK kills the helper JVM at main-class load with {@code UnsupportedClassVersionError} -- before
     * it can print a port, which surfaced only as the generic "exited before it reported a port"
     * card. A {@code null} version (an SDK whose version string {@code JavaSdk#getVersion} cannot
     * parse) is treated as too old: unknown is not worth gambling a hard crash on.
     *
     * <p>Falling back is always available -- the IDE's own runtime is at least Java 17 for every
     * build this plugin supports ({@code sinceBuild} 233.2 / IntelliJ 2023.3) -- and it is safe for
     * the module's compiled classes, which the helper JVM does load ({@link #launcherClasspath}):
     * the fallback only happens when the project SDK is <em>older</em> than 17, and bytecode that
     * SDK produced loads on a 17+ runtime. Matching the project's JDK is a preference, not a
     * requirement, and this keeps it whenever it is viable.
     *
     * <p>Package-visible and platform-free (it touches only the version enum) so
     * {@code LauncherJvmVersionGateTest} can exercise it without an {@code Application}.
     */
    static boolean canRunLauncherJar(JavaSdkVersion version) {
        return version != null && version.isAtLeast(MINIMUM_LAUNCHER_SDK);
    }

    private static Path resolveLauncherJar() {
        return launcherJarNextTo(PathManager.getJarForClass(ZulPreviewServerService.class));
    }

    /**
     * The bundled {@link #LAUNCHER_JAR_NAME}, given the jar this class runs from.
     *
     * <p>This used to ask the platform for our own descriptor
     * ({@code PluginManagerCore.getPlugin(...).getPluginPath()}), but that method became
     * {@code @ApiStatus.Internal} in the 2026.2 platform and the Marketplace compatibility check
     * rejects it -- and every drop-in descriptor lookup is internal too, so there is no supported way
     * to obtain it (see {@code tasks/internal-api-fix-plan.md}). Deriving the location from our own
     * jar needs no descriptor: {@code prepareSandbox} packages the launcher jar into the very same
     * {@code <plugin>/lib} directory this class is loaded from, so it is always a sibling.
     *
     * <p>Package-visible, static and platform-free so {@code LauncherJarLocationTest} can lock it.
     * The throw on an unlocatable jar keeps the previous contract: it happens inside the guarded
     * {@code commandLineSupplier}, so it surfaces as the preview error+Report card (see
     * {@link #startGuarded}).
     */
    static Path launcherJarNextTo(Path ownJar) {
        Path libDir = ownJar == null ? null : ownJar.getParent();
        if (libDir == null) {
            throw new IllegalStateException(
                    "Could not locate the '" + PLUGIN_ID + "' plugin installation directory");
        }
        return libDir.resolve(LAUNCHER_JAR_NAME);
    }

    private static String joinClasspath(List<File> jars) {
        return jars.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator));
    }

    /** Package-visible for {@code ResolveFailureDeliveryTest} (R2-CRIT3): the error card must name the root cause. */
    static String rootMessage(Throwable t) {
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
        /** Whether the module has usable ZK jars -- drives the R7/U3 gate in {@link #onTargetResolved}. */
        final ZkClasspathFilter.ZkPresence presence;
        final Path docroot;
        /** What the launcher gets on {@code --classpath} -- see {@link #launcherClasspath}. */
        final List<File> launcherClasspath;
        final String classpathSignature;
        final String requestPath;
        /** The three render-target facts a GitHub failure report needs, resolved once here. */
        final String buildSystem;
        final String layout;
        final String zkJarSummary;

        PreviewTarget(ZkClasspathFilter.ZkPresence presence, Path docroot, List<File> launcherClasspath,
                      String classpathSignature, String requestPath,
                      String buildSystem, String layout, String zkJarSummary) {
            this.presence = presence;
            this.docroot = docroot;
            this.launcherClasspath = launcherClasspath;
            this.classpathSignature = classpathSignature;
            this.requestPath = requestPath;
            this.buildSystem = buildSystem;
            this.layout = layout;
            this.zkJarSummary = zkJarSummary;
        }

        /** This target's facts as the report's environment block (see {@link PreviewIssueReporter}). */
        String reportEnvironment() {
            return PreviewIssueReporter.environment(buildSystem, layout, zkJarSummary);
        }
    }
}
