package org.zkoss.zkpreview.jakarta;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.zkoss.zkpreview.jakarta.mock.MockHttpSession;
import org.zkoss.zkpreview.testutil.ZkClasspathResolver;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * L1 (code review): the render engine must use a <em>fresh</em> {@link MockHttpSession} for each
 * render, not one shared session reused for the helper JVM's whole life -- otherwise every save
 * retains another ZK {@code Desktop}/component-tree in the one session (monotonic heap growth),
 * and separate preview tabs share a session they would not share on a real server. A recording
 * subclass observes that {@code newSession()} is invoked once per render.
 */
class JakartaSessionPerRenderTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");

    private static final class RecordingEngine extends JakartaRenderEngine {
        final List<MockHttpSession> created = new ArrayList<>();

        RecordingEngine(List<File> jars, Path webapp) {
            super(jars, webapp, null);
        }

        @Override
        MockHttpSession newSession() {
            MockHttpSession s = super.newSession();
            created.add(s);
            return s;
        }
    }

    @Test
    void eachRenderUsesItsOwnSession() throws Exception {
        ZkClasspathResolver.Resolution res = ZkClasspathResolver.resolveJakarta();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        RecordingEngine engine = new RecordingEngine(res.jars, FIXTURES);
        try {
            engine.renderZul("/plain.zul");
            engine.renderZul("/plain.zul");

            assertEquals(2, engine.created.size(),
                    "each render must create its own session (L1); the constructor must not create a shared one");
            assertNotSame(engine.created.get(0), engine.created.get(1),
                    "the two renders must not share a session (L1)");
        } finally {
            engine.close();
        }
    }
}
