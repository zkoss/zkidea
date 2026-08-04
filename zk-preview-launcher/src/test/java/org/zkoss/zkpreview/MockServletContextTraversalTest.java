package org.zkoss.zkpreview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * S1 (security): the mock servlet context must not serve files outside the docroot. A previewed
 * page's own client JS runs live in the JCEF pane, so an unsanitized {@code ../} in a resource
 * request would let a crafted {@code .zul} read arbitrary files the IDE process can access. This
 * mirrors the containment check {@code PreviewHttpServer.readZulSource} already applies. The two
 * servlet variants share byte-identical mock trees, so both are locked here.
 */
class MockServletContextTraversalTest {

    @TempDir
    Path tempDir;

    @Test
    void jakartaMockContextRefusesToEscapeTheDocroot() throws Exception {
        Path root = tempDir.resolve("docroot");
        Files.createDirectories(root.resolve("web"));
        Files.writeString(root.resolve("web/ok.txt"), "OK");
        Files.writeString(tempDir.resolve("secret.txt"), "SECRET"); // outside the docroot

        org.zkoss.zkpreview.jakarta.mock.MockServletContext ctx =
                new org.zkoss.zkpreview.jakarta.mock.MockServletContext(root);

        // A legit in-docroot resource still resolves.
        assertNotNull(ctx.getResource("/web/ok.txt"));
        try (InputStream in = ctx.getResourceAsStream("/web/ok.txt")) {
            assertNotNull(in);
        }
        // A traversal escaping the docroot must be denied (spec allows a null translation).
        assertNull(ctx.getResource("/web/../../secret.txt"), "getResource must not escape the docroot");
        assertNull(ctx.getResourceAsStream("/web/../../secret.txt"), "getResourceAsStream must not escape the docroot");
        assertNull(ctx.getRealPath("/web/../../secret.txt"), "getRealPath must not translate outside the docroot");
    }

    @Test
    void javaxMockContextRefusesToEscapeTheDocroot() throws Exception {
        Path root = tempDir.resolve("docroot");
        Files.createDirectories(root.resolve("web"));
        Files.writeString(root.resolve("web/ok.txt"), "OK");
        Files.writeString(tempDir.resolve("secret.txt"), "SECRET");

        org.zkoss.zkpreview.javax.mock.MockServletContext ctx =
                new org.zkoss.zkpreview.javax.mock.MockServletContext(root);

        assertNotNull(ctx.getResource("/web/ok.txt"));
        try (InputStream in = ctx.getResourceAsStream("/web/ok.txt")) {
            assertNotNull(in);
        }
        assertNull(ctx.getResource("/web/../../secret.txt"), "getResource must not escape the docroot");
        assertNull(ctx.getResourceAsStream("/web/../../secret.txt"), "getResourceAsStream must not escape the docroot");
        assertNull(ctx.getRealPath("/web/../../secret.txt"), "getRealPath must not translate outside the docroot");
    }
}
