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
 * Programmatic JUnit Platform entry point for the integration test.
 *
 * <p>Using {@code JavaExec} (not {@code Test}) as the Gradle task type avoids the
 * IntelliJ Platform Gradle Plugin hooking in its {@code PathClassLoader} / sandbox
 * setup, which would block on platform initialization when no IntelliJ APIs are
 * actually needed.
 */
public class IntegrationTestMain {

    public static void main(String[] args) {
        SummaryGeneratingListener listener = new SummaryGeneratingListener();

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(ZkPreviewServerIntegrationTest.class))
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
