package org.zkoss.zkidea.reference;

import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.zkoss.zkidea.dom.ZulDomUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides file path references for template URI arguments inside ZK binding annotations.
 * <p>
 * Handles patterns like {@code templateURI="@load('/path/to/file.zul')"} and
 * {@code @init('/path/to/file.zul')} where the argument is a web-context absolute path.
 * The web root is detected by walking up ancestor directories to find the nearest one
 * containing {@code WEB-INF/web.xml}.
 * </p>
 */
public class ZkTemplateUriReferenceProvider extends PsiReferenceProvider {

    private static final Logger LOG = Logger.getInstance(ZkTemplateUriReferenceProvider.class);

    /**
     * Matches @load or @init with a single-quoted or double-quoted absolute path argument.
     * The closing quote and ')' are intentionally omitted so the pattern also matches while
     * the user is still typing (i.e. before the annotation is fully closed).
     * Capture group 1: the path string (starting with /).
     */
    private static final Pattern TEMPLATE_URI_PATTERN =
            Pattern.compile("@(?:load|init)\\s*\\(\\s*['\"](/[^'\"()]*)");

    @Override
    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                            @NotNull ProcessingContext context) {
        if (!(element instanceof XmlAttributeValue)) return PsiReference.EMPTY_ARRAY;

        XmlAttributeValue attrValue = (XmlAttributeValue) element;
        if (!ZulDomUtil.isZKFile(element.getContainingFile())) return PsiReference.EMPTY_ARRAY;

        String rawValue = attrValue.getValue();
        if (rawValue == null || rawValue.isEmpty()) return PsiReference.EMPTY_ARRAY;

        // During completion IntelliJ inserts a dummy identifier at the caret position.
        // Strip it before matching so the pattern succeeds while the user is mid-typing.
        String value = rawValue
                .replace(CompletionUtilCore.DUMMY_IDENTIFIER, "")
                .replace(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, "");

        Matcher matcher = TEMPLATE_URI_PATTERN.matcher(value);
        if (!matcher.find()) return PsiReference.EMPTY_ARRAY;

        String path = matcher.group(1);
        LOG.debug("ZkTemplateUriReferenceProvider: matched template URI path: " + path);

        // Offset from PSI element start to the beginning of the value content (skips opening quote)
        int valueOffset = attrValue.getValueTextRange().getStartOffset()
                - attrValue.getTextRange().getStartOffset();

        // The path starts with '/', strip it and adjust the offset so that the FileReferenceSet
        // resolves path segments relative to the web root (not the filesystem root).
        int slashLen = path.startsWith("/") ? 1 : 0;
        String relativePath = path.substring(slashLen);
        int startOffset = valueOffset + matcher.start(1) + slashLen;

        // Use the original file so the virtual file lookup works inside a completion copy.
        VirtualFile zulVirtualFile = element.getContainingFile().getOriginalFile().getVirtualFile();
        if (zulVirtualFile == null) return PsiReference.EMPTY_ARRAY;

        VirtualFile webRoot = ZulWebRootResolver.findWebRoot(zulVirtualFile);
        if (webRoot == null) {
            LOG.debug("ZkTemplateUriReferenceProvider: no web root found for: " + zulVirtualFile.getPath());
            return PsiReference.EMPTY_ARRAY;
        }

        LOG.info("ZkTemplateUriReferenceProvider: resolving '" + path
                + "' against web root: " + webRoot.getPath());

        final VirtualFile resolvedWebRoot = webRoot;
        FileReferenceSet referenceSet = new FileReferenceSet(
                relativePath, element, startOffset, this, true) {
            @Override
            public @NotNull Collection<com.intellij.psi.PsiFileSystemItem> computeDefaultContexts() {
                com.intellij.psi.PsiDirectory dir =
                        PsiManager.getInstance(element.getProject()).findDirectory(resolvedWebRoot);
                if (dir != null) return Collections.singletonList(dir);
                return Collections.emptyList();
            }
        };

        FileReference[] refs = referenceSet.getAllReferences();
        LOG.debug("ZkTemplateUriReferenceProvider: created " + refs.length + " file reference(s) for: " + path);
        return refs;
    }
}
