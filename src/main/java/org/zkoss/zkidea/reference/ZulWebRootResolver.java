package org.zkoss.zkidea.reference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the web application root directory for a given ZUL file by walking up the
 * ancestor directories until a directory containing {@code WEB-INF/web.xml} is found.
 */
public class ZulWebRootResolver {

    private static final Logger LOG = Logger.getInstance(ZulWebRootResolver.class);

    private ZulWebRootResolver() {}

    /**
     * Walks up the ancestor directories of the given ZUL file and returns the first directory
     * that contains a {@code WEB-INF/web.xml} child, or {@code null} if none is found.
     *
     * @param zulFile the ZUL file whose web root is to be determined
     * @return the web root directory, or {@code null}
     */
    @Nullable
    public static VirtualFile findWebRoot(VirtualFile zulFile) {
        VirtualFile dir = zulFile.getParent();
        while (dir != null) {
            VirtualFile webInf = dir.findChild("WEB-INF");
            if (webInf != null && webInf.isDirectory()) {
                VirtualFile webXml = webInf.findChild("web.xml");
                if (webXml != null && !webXml.isDirectory()) {
                    LOG.info("ZulWebRootResolver: resolved web root at: " + dir.getPath());
                    return dir;
                }
            }
            dir = dir.getParent();
        }
        LOG.debug("ZulWebRootResolver: no WEB-INF/web.xml ancestor found for: " + zulFile.getPath());
        return null;
    }
}
