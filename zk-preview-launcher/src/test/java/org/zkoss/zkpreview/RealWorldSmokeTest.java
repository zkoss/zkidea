package org.zkoss.zkpreview;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.zkoss.zkpreview.testutil.ZkClasspathResolver;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Optional extra smoke coverage (not one of PLAN.md's six canonical gate fixtures):
 * renders real-world ZUL files from two Maven projects already present in this dev
 * environment, as a broader corpus check beyond the six controlled fixtures.
 * <ul>
 *   <li>jakarta: {@code manual-test/} (in-repo, ZK 10.1.0-jakarta)</li>
 *   <li>javax: {@code ~/Documents/workspace/SUPPORT/zk9support/} (ZK 9.6.6, read-only,
 *       749 real ZULs) -- both resolved via their own {@code pom.xml}</li>
 * </ul>
 * Both are Assumptions-gated: this test skips cleanly on a machine without one/both
 * of these external projects rather than false-failing.
 */
class RealWorldSmokeTest {

    @Test
    void jakartaManualTestPreviewFixtures() throws Exception {
        ZkClasspathResolver.Resolution res = ZkClasspathResolver.resolveJakarta();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        String repoRoot = System.getProperty("zkpreview.repoRoot", ".");
        Path webapp = Paths.get(repoRoot, "manual-test/src/main/webapp");
        Assumptions.assumeTrue(webapp.toFile().isDirectory(), "skip: " + webapp + " not found");

        try (RenderEngine engine = RenderEngineFactory.create(res.jars, webapp, null)) {
            RenderResult button = engine.renderZul("/preview/button.zul");
            assertTrue(button.isSuccess(), () -> describe(button));
            assertTrue(button.getHtml().contains("zul.wgt.Button"));
            assertTrue(button.getHtml().contains("submit"));

            RenderResult separateWpd = engine.renderZul("/preview/separate-wpd.zul");
            assertTrue(separateWpd.isSuccess(), () -> describe(separateWpd));
            assertTrue(separateWpd.getHtml().contains("zul.wnd.Window"));
            assertTrue(separateWpd.getHtml().contains("submit"));
        }
    }

    @Test
    void javaxZk9supportRealCorpus() throws Exception {
        ZkClasspathResolver.Resolution res = ZkClasspathResolver.resolveJavax();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        Path webapp = Paths.get(System.getProperty("user.home"),
                "Documents/workspace/SUPPORT/zk9support/src/main/webapp");
        Assumptions.assumeTrue(webapp.toFile().isDirectory(), "skip: " + webapp + " not found");

        try (RenderEngine engine = RenderEngineFactory.create(res.jars, webapp, null)) {
            RenderResult error = engine.renderZul("/error.zul");
            assertTrue(error.isSuccess(), () -> describe(error));

            RenderResult index = engine.renderZul("/index.zul");
            assertTrue(index.isSuccess(), () -> describe(index));

            RenderResult timer = engine.renderZul("/timer.zul");
            assertTrue(timer.isSuccess(), () -> describe(timer));
            assertTrue(timer.getHtml().contains("zul.wgt.Button"));

            // Exercises a real zscript block that DOES resolve (org.slf4j is on the ZK
            // classpath itself), contrasting with fixture (f)'s deliberately-missing class.
            RenderResult log = engine.renderZul("/log.zul");
            assertTrue(log.isSuccess(), () -> describe(log));
            assertTrue(log.getHtml().contains("test print log"));

            // A genuinely invalid real-world ZUL (two <north> children in one
            // <borderlayout>) -- demonstrates the structured-failure path also covers
            // ordinary ZK validation errors, not just missing-class scenarios.
            RenderResult invalid = engine.renderZul("/test.zul");
            assertFalse(invalid.isSuccess(), "test.zul is a known-invalid ZUL (duplicate <north>)");
            assertNotNull(invalid.getError().getMessage());
            assertTrue(invalid.getError().getMessage().contains("north"), invalid.getError().getMessage());
        }
    }

    private static String describe(RenderResult r) {
        return r.isSuccess() ? "(success)" : r.getError().toJson();
    }
}
