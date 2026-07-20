package org.zkoss.zkidea.preview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link DocrootResolver}'s docroot-resolution rule (E3 deliverable 6:
 * pure logic, no IntelliJ platform dependency -- real temp directories stand in for a
 * project's file tree).
 */
class DocrootResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesToAncestorContainingWebInf() throws IOException {
        Path webapp = tempDir.resolve("src/main/webapp");
        Files.createDirectories(webapp.resolve("WEB-INF"));
        Path zul = webapp.resolve("preview/button.zul");
        Files.createDirectories(zul.getParent());
        Files.createFile(zul);

        Path docroot = DocrootResolver.resolve(zul, List.of());

        assertEquals(webapp, docroot);
    }

    @Test
    void resolvesToAncestorNamedWebappEvenWithoutWebInf() throws IOException {
        // e.g. a webapp directory that hasn't been given a WEB-INF yet.
        Path webapp = tempDir.resolve("frontend/webapp");
        Path zul = webapp.resolve("index.zul");
        Files.createDirectories(zul.getParent());
        Files.createFile(zul);

        Path docroot = DocrootResolver.resolve(zul, List.of());

        assertEquals(webapp, docroot);
    }

    @Test
    void fallsBackToBoundaryRootWhenNoWebInfOrWebappFound() throws IOException {
        Path contentRoot = tempDir.resolve("src/main/resources");
        Path zul = contentRoot.resolve("pages/foo.zul");
        Files.createDirectories(zul.getParent());
        Files.createFile(zul);

        Path docroot = DocrootResolver.resolve(zul, List.of(contentRoot));

        assertEquals(contentRoot, docroot);
    }

    @Test
    void fallsBackToParentDirectoryWhenNoBoundaryRootMatches() throws IOException {
        Path dir = tempDir.resolve("loose");
        Path zul = dir.resolve("orphan.zul");
        Files.createDirectories(dir);
        Files.createFile(zul);

        Path unrelatedBoundary = tempDir.resolve("other-module");
        Files.createDirectories(unrelatedBoundary);

        Path docroot = DocrootResolver.resolve(zul, List.of(unrelatedBoundary));

        assertEquals(dir, docroot);
    }

    @Test
    void prefersNearestWebInfAncestorOverAFurtherOne() throws IOException {
        // A nested webapp (e.g. an embedded sample) should win over an outer one.
        Path outerWebapp = tempDir.resolve("outer/webapp");
        Files.createDirectories(outerWebapp.resolve("WEB-INF"));
        Path innerWebapp = outerWebapp.resolve("samples/inner-webapp");
        Files.createDirectories(innerWebapp.resolve("WEB-INF"));
        Path zul = innerWebapp.resolve("demo.zul");
        Files.createFile(zul);

        Path docroot = DocrootResolver.resolve(zul, List.of());

        assertEquals(innerWebapp, docroot);
    }
}
