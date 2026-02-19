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
