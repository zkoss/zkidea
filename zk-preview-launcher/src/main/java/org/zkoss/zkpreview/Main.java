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
 * --webapp <docroot dir> --port <port>} (port 0 = ephemeral). Prints the actual
 * bound port to stdout as {@code PREVIEW_PORT=<n>} (machine-parsable) then blocks
 * until the process is killed.
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parseArgs(args);
        String classpathArg = require(opts, "classpath");
        String webappArg = require(opts, "webapp");
        int port = Integer.parseInt(opts.getOrDefault("port", "0"));

        List<File> zkJars = new ArrayList<>();
        for (String entry : classpathArg.split(File.pathSeparator)) {
            if (!entry.isBlank()) zkJars.add(new File(entry.trim()));
        }
        Path webappDir = Paths.get(webappArg);

        // Detected here as well as inside the factory so the report can name it; it is a single
        // jar-entry read, and by construction it agrees with the engine the factory then builds.
        ZkVariant variant = VariantDetector.detect(zkJars);
        RenderEngine engine = RenderEngineFactory.create(zkJars, webappDir);
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
                    + " argument. Usage: --classpath <cp> --webapp <dir> --port <n>");
        }
        return v;
    }

    private Main() {
    }
}
