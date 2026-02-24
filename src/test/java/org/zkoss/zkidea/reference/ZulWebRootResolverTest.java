package org.zkoss.zkidea.reference;

import com.intellij.openapi.vfs.VirtualFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ZulWebRootResolver.findWebRoot().
 */
@ExtendWith(MockitoExtension.class)
public class ZulWebRootResolverTest {

    /**
     * Test: ZUL file whose nearest WEB-INF/web.xml is two directory levels up.
     * Structure: /webRoot/pages/sub/test.zul, web root at /webRoot
     */
    @Test
    void testStandardCase_webRootTwoLevelsUp() {
        VirtualFile webRoot = mock(VirtualFile.class);
        VirtualFile webInf = mock(VirtualFile.class);
        VirtualFile webXml = mock(VirtualFile.class);
        VirtualFile pages = mock(VirtualFile.class);
        VirtualFile sub = mock(VirtualFile.class);
        VirtualFile zulFile = mock(VirtualFile.class);

        when(zulFile.getParent()).thenReturn(sub);
        when(sub.findChild("WEB-INF")).thenReturn(null);
        when(sub.getParent()).thenReturn(pages);
        when(pages.findChild("WEB-INF")).thenReturn(null);
        when(pages.getParent()).thenReturn(webRoot);
        when(webRoot.findChild("WEB-INF")).thenReturn(webInf);
        when(webInf.isDirectory()).thenReturn(true);
        when(webInf.findChild("web.xml")).thenReturn(webXml);
        when(webXml.isDirectory()).thenReturn(false);
        when(webRoot.getPath()).thenReturn("/webRoot");

        VirtualFile result = ZulWebRootResolver.findWebRoot(zulFile);

        assertSame(webRoot, result);
    }

    /**
     * Test: ZUL file nested under a sub-application with its own WEB-INF/web.xml.
     * The nearest (inner) web root must win over the outer one.
     * Structure:
     *   /root/WEB-INF/web.xml           (outer)
     *   /root/sub-app/WEB-INF/web.xml   (inner — nearest wins)
     *   /root/sub-app/views/test.zul
     */
    @Test
    void testMultiWebXml_nearestWins() {
        VirtualFile subApp = mock(VirtualFile.class);
        VirtualFile subWebInf = mock(VirtualFile.class);
        VirtualFile subWebXml = mock(VirtualFile.class);
        VirtualFile views = mock(VirtualFile.class);
        VirtualFile zulFile = mock(VirtualFile.class);

        when(zulFile.getParent()).thenReturn(views);
        when(views.findChild("WEB-INF")).thenReturn(null);
        when(views.getParent()).thenReturn(subApp);
        when(subApp.findChild("WEB-INF")).thenReturn(subWebInf);
        when(subWebInf.isDirectory()).thenReturn(true);
        when(subWebInf.findChild("web.xml")).thenReturn(subWebXml);
        when(subWebXml.isDirectory()).thenReturn(false);
        when(subApp.getPath()).thenReturn("/root/sub-app");

        // root-level mocks are intentionally NOT set up — we should never reach them
        VirtualFile result = ZulWebRootResolver.findWebRoot(zulFile);

        assertSame(subApp, result);
    }

    /**
     * ISA Scenario 1: ZUL file whose immediate parent contains WEB-INF/web.xml.
     * No walking required — parent IS the web root.
     */
    @Test
    void findWebRoot_returnsImmediateParent_whenItContainsWebInfWebXml() {
        VirtualFile webRoot = mock(VirtualFile.class);
        VirtualFile webInf  = mock(VirtualFile.class);
        VirtualFile webXml  = mock(VirtualFile.class);
        VirtualFile zulFile = mock(VirtualFile.class);

        when(zulFile.getParent()).thenReturn(webRoot);
        when(webRoot.findChild("WEB-INF")).thenReturn(webInf);
        when(webInf.isDirectory()).thenReturn(true);
        when(webInf.findChild("web.xml")).thenReturn(webXml);
        when(webXml.isDirectory()).thenReturn(false);
        when(webRoot.getPath()).thenReturn("/webRoot");

        assertSame(webRoot, ZulWebRootResolver.findWebRoot(zulFile));
    }

    /**
     * ISA Scenario 5: WEB-INF directory exists but contains no web.xml child.
     * findChild("web.xml") returns null → check fails; loop continues.
     */
    @Test
    void findWebRoot_skipsAncestor_whenWebInfHasNoWebXml() {
        VirtualFile goodDir    = mock(VirtualFile.class);
        VirtualFile goodWebInf = mock(VirtualFile.class);
        VirtualFile goodWebXml = mock(VirtualFile.class);
        VirtualFile badDir     = mock(VirtualFile.class);
        VirtualFile badWebInf  = mock(VirtualFile.class);
        VirtualFile zulFile    = mock(VirtualFile.class);

        when(zulFile.getParent()).thenReturn(badDir);
        when(badDir.findChild("WEB-INF")).thenReturn(badWebInf);
        when(badWebInf.isDirectory()).thenReturn(true);
        when(badWebInf.findChild("web.xml")).thenReturn(null);   // no web.xml — skip
        when(badDir.getParent()).thenReturn(goodDir);
        when(goodDir.findChild("WEB-INF")).thenReturn(goodWebInf);
        when(goodWebInf.isDirectory()).thenReturn(true);
        when(goodWebInf.findChild("web.xml")).thenReturn(goodWebXml);
        when(goodWebXml.isDirectory()).thenReturn(false);
        when(goodDir.getPath()).thenReturn("/goodDir");

        assertSame(goodDir, ZulWebRootResolver.findWebRoot(zulFile));
    }

    /**
     * Test: ZUL file with no WEB-INF/web.xml anywhere above it — must return null.
     */
    @Test
    void testNoWebRoot_returnsNull() {
        VirtualFile parent = mock(VirtualFile.class);
        VirtualFile zulFile = mock(VirtualFile.class);

        when(zulFile.getParent()).thenReturn(parent);
        when(parent.findChild("WEB-INF")).thenReturn(null);
        when(parent.getParent()).thenReturn(null);
        when(zulFile.getPath()).thenReturn("/somewhere/test.zul");

        VirtualFile result = ZulWebRootResolver.findWebRoot(zulFile);

        assertNull(result);
    }
}
