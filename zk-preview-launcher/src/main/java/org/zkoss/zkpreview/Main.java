package org.zkoss.zkpreview;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

        RenderEngine engine = RenderEngineFactory.create(zkJars, webappDir);
        PreviewHttpServer server = new PreviewHttpServer(engine, port, reportEnv(opts), webappDir);
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
     * Environment block for the error page's "Report on GitHub" link. Plugin/IDE identity
     * comes from the plugin (optional {@code --report-plugin}/{@code --report-ide}); OS/JDK
     * are this (launcher) JVM's, which is the JVM actually running ZK. Returns {@code null}
     * when the plugin passed no identity (e.g. the standalone CLI), so the link still works
     * but omits the env block.
     */
    private static String reportEnv(Map<String, String> opts) {
        String plugin = opts.get("report-plugin");
        String ide = opts.get("report-ide");
        if (plugin == null && ide == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (plugin != null) sb.append("Plugin: ").append(plugin).append('\n');
        if (ide != null) sb.append("IDE: ").append(ide).append('\n');
        sb.append("OS: ").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.version")).append('\n');
        sb.append("JDK: ").append(System.getProperty("java.version"));
        return sb.toString();
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
