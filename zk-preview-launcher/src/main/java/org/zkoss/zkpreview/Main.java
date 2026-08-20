package org.zkoss.zkpreview;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * CLI: {@code java -jar zk-preview-launcher.jar --classpath <os-separated ZK jars>
 * --webapp <docroot dir> --port <port> [--isolation on|off] [--controller-timeout <seconds>]}
 * (port 0 = ephemeral). Prints the actual bound port to stdout as {@code PREVIEW_PORT=<n>}
 * (machine-parsable) then blocks until the process is killed.
 *
 * <p>{@code --isolation off} runs the previewed project's real Composers/ViewModels, i.e. it
 * <b>executes arbitrary project code</b> (P0-2). It is opt-in: absent the option the process
 * default from {@link IsolationMode} applies, which is isolation on -- what the IntelliJ plugin
 * relies on. {@code PREVIEW_PORT=} stays the only thing this class writes to stdout, in either
 * mode; the per-render mode is reported on each response's
 * {@code X-ZK-Preview-Controllers} header (see {@link PreviewHttpServer}).
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parseArgs(args);
        String classpathArg = require(opts, "classpath");
        String webappArg = require(opts, "webapp");
        int port = Integer.parseInt(opts.getOrDefault("port", "0"));
        ControllerPolicy controllerPolicy = controllerPolicy(opts);

        List<File> zkJars = new ArrayList<>();
        for (String entry : classpathArg.split(File.pathSeparator)) {
            if (!entry.isBlank()) zkJars.add(new File(entry.trim()));
        }
        Path webappDir = Paths.get(webappArg);

        // Detected here as well as inside the factory so the report can name it; it is a single
        // jar-entry read, and by construction it agrees with the engine the factory then builds.
        ZkVariant variant = VariantDetector.detect(zkJars);
        RenderEngine engine = RenderEngineFactory.create(zkJars, webappDir, null, controllerPolicy);
        PreviewHttpServer server = new PreviewHttpServer(engine, port, reportEnv(opts, variant), webappDir);
        server.start();

        System.out.println("PREVIEW_PORT=" + server.getPort());
        System.out.flush();

        CountDownLatch shutdown = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            shutdown.countDown();
        }));
        shutdown.await();
    }

    /**
     * Builds the render-time controller policy from {@code --isolation on|off} and
     * {@code --controller-timeout <seconds>}.
     *
     * <p>Absent {@code --isolation}, the {@link IsolationMode} process default applies, so
     * {@code -Dzkpreview.isolation=false} still works as the raw hooks-level switch. Present, it
     * is an explicit choice and wins over that property in both directions -- {@code on} pins
     * isolation on even with the property set to {@code false}, which is what makes the option
     * worth having.
     *
     * <p>Both parse strictly. A mistyped value must fail fast rather than fall back to isolated:
     * a silently isolated render looks exactly like a successful {@code --isolation off} one apart
     * from the reported mode, and the caller's judging rules invert on that.
     */
    static ControllerPolicy controllerPolicy(Map<String, String> opts) {
        String isolation = opts.get("isolation");
        String timeout = opts.get("controller-timeout");
        int timeoutSeconds = ControllerPolicy.DEFAULT_TIMEOUT_SECONDS;
        if (timeout != null) {
            try {
                timeoutSeconds = Integer.parseInt(timeout.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid --controller-timeout value '" + timeout
                        + "'. Expected a positive number of seconds.");
            }
            if (timeoutSeconds <= 0) {
                throw new IllegalArgumentException("Invalid --controller-timeout value '" + timeout
                        + "'. Expected a positive number of seconds.");
            }
        }
        if (isolation == null) {
            return ControllerPolicy.fromProcessDefault();
        }
        return ControllerPolicy.of(!IsolationMode.parse(isolation), timeoutSeconds);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--") && i + 1 < args.length) {
                opts.put(a.substring(2), args[++i]);
            }
        }
        return opts;
    }

    /**
     * Environment block for the error page's "Report on GitHub" link -- the report path a render
     * failure actually takes, so it must describe how the page was set up to render, not just who
     * was running.
     *
     * <p>Split of knowledge: the plugin passes what only it can know -- its identity
     * ({@code --report-plugin}/{@code --report-ide}), the build tool that imported the module
     * ({@code --report-build}), which docroot rule matched ({@code --report-layout}) and the
     * resolved ZK jars ({@code --report-zkjars}, formatted plugin-side so "what counts as a ZK jar"
     * stays defined in exactly one place). This JVM adds what only it knows: its own OS/JDK -- the
     * JVM actually running ZK -- and the servlet variant it detected. Any fact not supplied is
     * omitted.
     *
     * <p>Returns {@code null} when no identity was passed at all (e.g. the standalone CLI), so the
     * link still works but omits the env block.
     *
     * <p>Label set and order mirror the plugin's own assembler
     * ({@code PreviewIssueReporter.renderEnvironment}) so the same failure reads identically
     * whichever report path produced it; {@code ReportEnvTest} locks the list on this side.
     */
    static String reportEnv(Map<String, String> opts, ZkVariant variant) {
        String plugin = opts.get("report-plugin");
        String ide = opts.get("report-ide");
        if (plugin == null && ide == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        appendFact(sb, "Plugin", plugin);
        appendFact(sb, "IDE", ide);
        appendFact(sb, "OS", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        appendFact(sb, "JDK", System.getProperty("java.version"));
        appendFact(sb, "Build", opts.get("report-build"));
        appendFact(sb, "Layout", opts.get("report-layout"));
        appendFact(sb, "Servlet", variant == null ? null : variant.name().toLowerCase(Locale.ROOT));
        appendFact(sb, "ZK jars", opts.get("report-zkjars"));
        return sb.toString();
    }

    /** One {@code "Label: value"} line; an undetermined fact vanishes rather than printing blank. */
    private static void appendFact(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(label).append(": ").append(value);
    }

    private static String require(Map<String, String> opts, String key) {
        String v = opts.get(key);
        if (v == null) {
            throw new IllegalArgumentException("Missing required --" + key
                    + " argument. Usage: --classpath <cp> --webapp <dir> --port <n>"
                    + " [--isolation on|off] [--controller-timeout <seconds>]");
        }
        return v;
    }

    private Main() {
    }
}
