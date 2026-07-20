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
        PreviewHttpServer server = new PreviewHttpServer(engine, port);
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
