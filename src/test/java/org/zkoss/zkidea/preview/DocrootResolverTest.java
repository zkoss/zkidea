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

    // --- Spring Boot jar layout (P4): pages on the classpath under src/main/resources/web/,
    //     no src/main/webapp and no WEB-INF. The classpath web root must be the docroot so the
    //     page is served at its production url (/index.zul), not /src/main/resources/web/index.zul.

    @Test
    void resolvesToClasspathWebRootForSpringBootJarNestedPage() throws IOException {
        Path resourceRoot = tempDir.resolve("src/main/resources");
        Path webRoot = resourceRoot.resolve("web");
        Path zul = webRoot.resolve("zul/page.zul");
        Files.createDirectories(zul.getParent());
        Files.createFile(zul);

        // boundaryRoots = module content roots (as in production); resourceRoots = the RESOURCE source root.
        Path docroot = DocrootResolver.resolve(zul, List.of(tempDir), List.of(resourceRoot));

        assertEquals(webRoot, docroot);
    }

    @Test
    void resolvesToClasspathWebRootForSpringBootJarTopLevelPage() throws IOException {
        Path resourceRoot = tempDir.resolve("src/main/resources");
        Path webRoot = resourceRoot.resolve("web");
        Path zul = webRoot.resolve("index.zul");
        Files.createDirectories(webRoot);
        Files.createFile(zul);

        Path docroot = DocrootResolver.resolve(zul, List.of(tempDir), List.of(resourceRoot));

        // docroot == web root => the request path relativizes to /index.zul (the production url).
        assertEquals(webRoot, docroot);
    }

    @Test
    void doesNotTreatAnUnrelatedWebDirAsClasspathWebRoot() throws IOException {
        // A directory literally named "web" that is NOT directly under a resource root must not
        // be mistaken for ZK's classpath web root; resolution falls back to the boundary root.
        Path moduleRoot = tempDir.resolve("proj");
        Path strayWeb = moduleRoot.resolve("frontend/web");
        Path zul = strayWeb.resolve("thing.zul");
        Files.createDirectories(zul.getParent());
        Files.createFile(zul);

        Path unrelatedResourceRoot = moduleRoot.resolve("src/main/resources");
        Files.createDirectories(unrelatedResourceRoot);

        Path docroot = DocrootResolver.resolve(zul, List.of(moduleRoot), List.of(unrelatedResourceRoot));

        assertEquals(moduleRoot, docroot);
    }

    @Test
    void warLayoutStillWinsOverAClasspathWebRootWhenBothCouldMatch() throws IOException {
        // Defensive ordering: a page under a WEB-INF webapp resolves to the webapp even if a
        // resources/web exists in the same module (the WAR rule is checked first).
        Path webapp = tempDir.resolve("src/main/webapp");
        Files.createDirectories(webapp.resolve("WEB-INF"));
        Path zul = webapp.resolve("page.zul");
        Files.createFile(zul);

        Path resourceRoot = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourceRoot.resolve("web"));

        Path docroot = DocrootResolver.resolve(zul, List.of(tempDir), List.of(resourceRoot));

        assertEquals(webapp, docroot);
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
