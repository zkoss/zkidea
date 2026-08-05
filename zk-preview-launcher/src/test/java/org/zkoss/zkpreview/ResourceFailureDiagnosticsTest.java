package org.zkoss.zkpreview;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R2-CRIT2 (code review #2): {@code AbstractRenderEngine.resource()} used to collapse <em>every</em>
 * failure -- a servlet throw and any status &ge; 400 alike -- into the same empty
 * {@link ResourceResult#notFound()} a plain typo'd path gets, with no log line anywhere. Unlike
 * {@code renderZul} (which unwraps, classifies via {@code ErrorMapper} and renders a diagnostic page),
 * a broken {@code /zkau/web/*} fetch of ZK's own CSS/JS left zero trace: the developer sees an
 * unstyled skeleton of a page that itself rendered fine, so no error card appears and the
 * "Report this issue on GitHub" flow never even triggers.
 *
 * <p>These are the two outcome branches of {@code resource()}, exercised directly -- they are
 * deliberately platform-free statics so this needs no ZK jar and no bootstrapped engine.
 * The returned {@code ResourceResult} is unchanged by the fix; what is asserted is that the
 * failure is no longer silent.
 */
class ResourceFailureDiagnosticsTest {

    private static final String PATH = "/web/_zv1/js/zk/zk.wpd";

    private PrintStream realErr;
    private ByteArrayOutputStream captured;

    @BeforeEach
    void captureStderr() {
        realErr = System.err;
        captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStderr() {
        System.setErr(realErr);
    }

    private String stderr() {
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Test
    void aThrownResourceFailureIsReportedOnStderr() {
        ResourceResult result = AbstractRenderEngine.resourceFailure(PATH,
                new IllegalStateException("ClassWebResource is not initialized"));

        assertFalse(result.isFound(), "the served outcome is unchanged -- still a 404");
        String err = stderr();
        assertTrue(err.contains(PATH), "the diagnostic must name the failing resource path: " + err);
        assertTrue(err.contains("ClassWebResource is not initialized"),
                "the diagnostic must carry the real failure message: " + err);
    }

    @Test
    void aReflectiveWrapperIsUnwrappedSoTheRealCauseIsLogged() {
        AbstractRenderEngine.resourceFailure(PATH,
                new InvocationTargetException(new NullPointerException("no Execution bound")));

        String err = stderr();
        assertTrue(err.contains("no Execution bound"),
                "the reflection wrapper must not hide the real cause: " + err);
    }

    @Test
    void anErrorStatusFromTheServletIsReportedOnStderr() {
        ResourceResult result = AbstractRenderEngine.resourceOutcome(PATH, 500, "text/html",
                "Extendlet failure".getBytes(StandardCharsets.UTF_8));

        assertFalse(result.isFound(), "the served outcome is unchanged -- still a 404");
        String err = stderr();
        assertTrue(err.contains(PATH), "the diagnostic must name the failing resource path: " + err);
        assertTrue(err.contains("500"), "the diagnostic must carry the real status: " + err);
    }

    @Test
    void aSuccessfulResourceFetchStaysSilentAndIsPassedThroughUnchanged() {
        byte[] body = "zk.load()".getBytes(StandardCharsets.UTF_8);

        ResourceResult result = AbstractRenderEngine.resourceOutcome(PATH, 200, "text/javascript", body);

        assertTrue(result.isFound());
        assertEquals(200, result.getStatus());
        assertEquals("text/javascript", result.getContentType());
        assertArrayEquals(body, result.getBody());
        assertEquals("", stderr(), "a healthy asset fetch must not log anything");
    }
}
