package org.zkoss.zkidea.preview;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;

/**
 * Programmatic JUnit Platform entry point for the mock-servlet render test.
 *
 * <p>Invoked by the {@code mockRenderTest} Gradle {@code JavaExec} task, which
 * bypasses IntelliJ Platform Gradle Plugin's sandbox hooks.
 */
public class ZkMockRendererTestMain {

    public static void main(String[] args) {
        SummaryGeneratingListener listener = new SummaryGeneratingListener();

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(ZkMockServletRenderTest.class))
                .build();

        Launcher launcher = LauncherFactory.create();
        launcher.execute(request, listener);

        TestExecutionSummary summary = listener.getSummary();
        PrintWriter out = new PrintWriter(System.out, true);
        summary.printTo(out);

        if (summary.getTestsFailedCount() > 0) {
            summary.printFailuresTo(new PrintWriter(System.err, true));
            System.exit(1);
        }
    }
}
