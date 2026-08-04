package org.zkoss.zkidea.preview;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.KillableProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import com.intellij.util.io.BaseOutputReader;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Owns one spawned {@code zk-preview-launcher} helper JVM: starts it, parses the
 * {@code PREVIEW_PORT=<n>} line it prints on stdout once ready (per the launcher's CLI
 * contract, see {@code zk-preview-launcher}'s {@code Main.java}), and kills it on
 * {@link #destroy()}.
 *
 * <p>Deliberately has no dependency on {@link com.intellij.openapi.project.Project} or
 * any other project-model API, so its "does destroy() actually kill the OS process"
 * teardown contract (E3-G2) can be exercised directly by a lightweight, non-platform
 * JUnit test using a short-lived stand-in process instead of the real launcher jar.
 */
final class ManagedPreviewServer {

    private static final Pattern PORT_LINE = Pattern.compile("PREVIEW_PORT=(\\d+)");
    private static final int STDERR_TAIL_LIMIT = 2000;

    private final KillableProcessHandler handler;
    private final CompletableFuture<Integer> portFuture = new CompletableFuture<>();
    private final StringBuilder stderrTail = new StringBuilder();

    ManagedPreviewServer(GeneralCommandLine commandLine) throws ExecutionException {
        this.handler = new KillableProcessHandler(commandLine) {
            @Override
            protected BaseOutputReader.Options readerOptions() {
                // lsp4ij precedent (RESEARCH.md U5-F19): a long-lived, mostly-silent
                // daemon process should not be polled aggressively.
                return BaseOutputReader.Options.forMostlySilentProcess();
            }
        };
        handler.addProcessListener(new ProcessAdapter() {
            @Override
            public void onTextAvailable(ProcessEvent event, Key outputType) {
                if (outputType == ProcessOutputTypes.STDOUT) {
                    Matcher m = PORT_LINE.matcher(event.getText());
                    if (m.find() && !portFuture.isDone()) {
                        portFuture.complete(Integer.parseInt(m.group(1)));
                    }
                } else if (outputType == ProcessOutputTypes.STDERR) {
                    synchronized (stderrTail) {
                        stderrTail.append(event.getText());
                    }
                }
            }

            @Override
            public void processTerminated(ProcessEvent event) {
                if (!portFuture.isDone()) {
                    portFuture.completeExceptionally(new IllegalStateException(
                            "zk-preview-launcher exited (exit code " + event.getExitCode()
                                    + ") before it reported a port. stderr: " + tail()));
                }
            }
        });
    }

    /** A pseudo-server representing a launch that failed before a process even started. */
    private ManagedPreviewServer(Throwable failure) {
        this.handler = null;
        portFuture.completeExceptionally(failure);
    }

    static ManagedPreviewServer failed(Throwable failure) {
        return new ManagedPreviewServer(failure);
    }

    void start() {
        if (handler != null) {
            handler.startNotify();
        }
    }

    CompletableFuture<Integer> portFuture() {
        return portFuture;
    }

    boolean isAlive() {
        return handler != null && !handler.isProcessTerminated();
    }

    void destroy() {
        if (handler != null && !handler.isProcessTerminated()) {
            handler.destroyProcess();
        }
    }

    /** Test convenience: blocks until the process exits or the timeout elapses. */
    boolean awaitTermination(long timeout, TimeUnit unit) {
        return handler == null || handler.waitFor(unit.toMillis(timeout));
    }

    private String tail() {
        synchronized (stderrTail) {
            String s = stderrTail.toString();
            return s.length() > STDERR_TAIL_LIMIT ? s.substring(s.length() - STDERR_TAIL_LIMIT) : s;
        }
    }
}
