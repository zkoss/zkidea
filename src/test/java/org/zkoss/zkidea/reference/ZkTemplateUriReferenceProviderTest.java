package org.zkoss.zkidea.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ProcessingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zkoss.zkidea.dom.ZulDomUtil;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ZkTemplateUriReferenceProvider#getReferencesByElement}.
 *
 * <p>One test method per scenario in features/template-uri-navigation.feature (13 total).
 * The Scenario Outline (4 examples) maps to a single test that loops through all examples.</p>
 *
 * <p>Strategy:
 * <ul>
 *   <li>{@code mockStatic(ZulDomUtil.class)} stubs {@code isZKFile} so tests never
 *       hit IntelliJ's file-type registry.</li>
 *   <li>{@code mockStatic(ZulWebRootResolver.class)} stubs {@code findWebRoot} so
 *       tests control whether a web root is "found" without needing a real VFS tree.</li>
 *   <li>PSI/VFS interfaces are plain Mockito mocks — no IntelliJ platform
 *       initialisation required.</li>
 *   <li>Non-ZUL file scenario uses a plain {@code PsiFile} mock (not {@code XmlFile})
 *       so the real {@code ZulDomUtil.isZKFile} runs without static mocking.</li>
 * </ul>
 * </p>
 *
 * <p>Text-range convention for all {@code zulAttr(value)} mocks:
 * <pre>
 *   getTextRange()      = [0, len+2)
 *   getValueTextRange() = [1, len+1)
 *   → valueOffset = 1
 * </pre>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ZkTemplateUriReferenceProviderTest {

    private final ZkTemplateUriReferenceProvider provider = new ZkTemplateUriReferenceProvider();

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a mock {@link XmlAttributeValue} backed by an {@link XmlFile} with
     * standard text-range metadata (valueOffset = 1).
     */
    private XmlAttributeValue zulAttr(String value) {
        XmlAttributeValue attr = mock(XmlAttributeValue.class);
        XmlFile mockFile = mock(XmlFile.class);
        when(attr.getContainingFile()).thenReturn(mockFile);
        lenient().when(attr.getValue()).thenReturn(value);
        lenient().when(attr.getTextRange())
                .thenReturn(new TextRange(0, value.length() + 2));
        lenient().when(attr.getValueTextRange())
                .thenReturn(new TextRange(1, value.length() + 1));
        return attr;
    }

    /**
     * Stubs the {@code getContainingFile().getOriginalFile().getVirtualFile()} chain
     * to return the provided {@link VirtualFile}.
     */
    private void stubVirtualFile(XmlAttributeValue attr, VirtualFile vf) {
        XmlFile containingFile = (XmlFile) attr.getContainingFile();
        XmlFile originalFile = mock(XmlFile.class);
        when(containingFile.getOriginalFile()).thenReturn(originalFile);
        when(originalFile.getVirtualFile()).thenReturn(vf);
    }

    /**
     * Wires all "happy path" stubs: isZKFile → true, virtualFile → mockVf,
     * findWebRoot → mockWebRoot.  Returns the mocked web-root VirtualFile.
     */
    private VirtualFile setupHappyPath(MockedStatic<ZulDomUtil> domUtil,
                                        MockedStatic<ZulWebRootResolver> resolver,
                                        XmlAttributeValue attr) {
        domUtil.when(() -> ZulDomUtil.isZKFile(any(PsiFile.class))).thenReturn(true);

        VirtualFile mockVf = mock(VirtualFile.class);
        stubVirtualFile(attr, mockVf);

        VirtualFile mockWebRoot = mock(VirtualFile.class);
        resolver.when(() -> ZulWebRootResolver.findWebRoot(mockVf)).thenReturn(mockWebRoot);

        return mockWebRoot;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: Navigate to a template file via @load — single-quoted path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void load_singleQuotedAbsolutePath_returnsFileReferences() {
        XmlAttributeValue attr = zulAttr("@load('/WEB-INF/template/grid.zul')");

        try (MockedStatic<ZulDomUtil> domUtil = mockStatic(ZulDomUtil.class);
             MockedStatic<ZulWebRootResolver> resolver = mockStatic(ZulWebRootResolver.class)) {

            setupHappyPath(domUtil, resolver, attr);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            assertNotSame(PsiReference.EMPTY_ARRAY, refs);
            assertTrue(refs.length > 0);
            assertInstanceOf(FileReference.class, refs[0]);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: Navigate to a template file via @init — single-quoted path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void init_singleQuotedAbsolutePath_returnsFileReferences() {
        XmlAttributeValue attr = zulAttr("@init('/WEB-INF/template/item.zul')");

        try (MockedStatic<ZulDomUtil> domUtil = mockStatic(ZulDomUtil.class);
             MockedStatic<ZulWebRootResolver> resolver = mockStatic(ZulWebRootResolver.class)) {

            setupHappyPath(domUtil, resolver, attr);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            assertNotSame(PsiReference.EMPTY_ARRAY, refs);
            assertTrue(refs.length > 0);
            assertInstanceOf(FileReference.class, refs[0]);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: Navigate to a template file via @load — double-quoted path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void load_doubleQuotedAbsolutePath_returnsFileReferences() {
        XmlAttributeValue attr = zulAttr("@load(\"/WEB-INF/template/grid.zul\")");

        try (MockedStatic<ZulDomUtil> domUtil = mockStatic(ZulDomUtil.class);
             MockedStatic<ZulWebRootResolver> resolver = mockStatic(ZulWebRootResolver.class)) {

            setupHappyPath(domUtil, resolver, attr);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            assertNotSame(PsiReference.EMPTY_ARRAY, refs);
            assertTrue(refs.length > 0);
            assertInstanceOf(FileReference.class, refs[0]);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: Navigate to a template file when optional whitespace follows the opening paren
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void load_whitespaceBeforeQuote_returnsFileReferences() {
        XmlAttributeValue attr = zulAttr("@load( '/WEB-INF/grid.zul')");

        try (MockedStatic<ZulDomUtil> domUtil = mockStatic(ZulDomUtil.class);
             MockedStatic<ZulWebRootResolver> resolver = mockStatic(ZulWebRootResolver.class)) {

            setupHappyPath(domUtil, resolver, attr);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            assertNotSame(PsiReference.EMPTY_ARRAY, refs);
            assertTrue(refs.length > 0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: File path completion is available while the path is still being typed
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void partialPath_duringTyping_returnsFileReferences() {
        // No closing quote or ')' — simulates typing in progress
        XmlAttributeValue attr = zulAttr("@load('/WEB-INF/template/");

        try (MockedStatic<ZulDomUtil> domUtil = mockStatic(ZulDomUtil.class);
             MockedStatic<ZulWebRootResolver> resolver = mockStatic(ZulWebRootResolver.class)) {

            setupHappyPath(domUtil, resolver, attr);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            assertNotSame(PsiReference.EMPTY_ARRAY, refs);
            assertTrue(refs.length > 0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: Web root is the ZUL file's immediate parent directory
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void webRoot_immediateParent_referencesCreated() {
        XmlAttributeValue attr = zulAttr("@load('/WEB-INF/template/grid.zul')");

        try (MockedStatic<ZulDomUtil> domUtil = mockStatic(ZulDomUtil.class);
             MockedStatic<ZulWebRootResolver> resolver = mockStatic(ZulWebRootResolver.class)) {

            domUtil.when(() -> ZulDomUtil.isZKFile(any(PsiFile.class))).thenReturn(true);

            VirtualFile mockVf = mock(VirtualFile.class);
            stubVirtualFile(attr, mockVf);

            VirtualFile webRoot = mock(VirtualFile.class);
            resolver.when(() -> ZulWebRootResolver.findWebRoot(mockVf)).thenReturn(webRoot);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            assertNotSame(PsiReference.EMPTY_ARRAY, refs);
            resolver.verify(() -> ZulWebRootResolver.findWebRoot(mockVf));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: Web root is found by walking up one directory level
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void webRoot_foundOneDirectoryLevelUp_referencesCreated() {
        XmlAttributeValue attr = zulAttr("@load('/WEB-INF/template/grid.zul')");

        try (MockedStatic<ZulDomUtil> domUtil = mockStatic(ZulDomUtil.class);
             MockedStatic<ZulWebRootResolver> resolver = mockStatic(ZulWebRootResolver.class)) {

            setupHappyPath(domUtil, resolver, attr);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            assertNotSame(PsiReference.EMPTY_ARRAY, refs);
            assertTrue(refs.length > 0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: Web root resolution skips an ancestor whose WEB-INF directory has no web.xml
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void webRoot_skipsAncestorWithWebInfButNoWebXml_referencesCreated() {
        XmlAttributeValue attr = zulAttr("@load('/WEB-INF/template/grid.zul')");

        try (MockedStatic<ZulDomUtil> domUtil = mockStatic(ZulDomUtil.class);
             MockedStatic<ZulWebRootResolver> resolver = mockStatic(ZulWebRootResolver.class)) {

            domUtil.when(() -> ZulDomUtil.isZKFile(any(PsiFile.class))).thenReturn(true);

            VirtualFile mockVf = mock(VirtualFile.class);
            stubVirtualFile(attr, mockVf);

            // Resolver skipped the incomplete ancestor and returned the correct web root
            VirtualFile correctWebRoot = mock(VirtualFile.class);
            resolver.when(() -> ZulWebRootResolver.findWebRoot(mockVf)).thenReturn(correctWebRoot);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            assertNotSame(PsiReference.EMPTY_ARRAY, refs);
            assertTrue(refs.length > 0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: No navigation when no ancestor directory contains WEB-INF/web.xml
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void noWebRoot_returnsEmptyArray() {
        XmlAttributeValue attr = zulAttr("@load('/WEB-INF/template/grid.zul')");

        try (MockedStatic<ZulDomUtil> domUtil = mockStatic(ZulDomUtil.class);
             MockedStatic<ZulWebRootResolver> resolver = mockStatic(ZulWebRootResolver.class)) {

            domUtil.when(() -> ZulDomUtil.isZKFile(any(PsiFile.class))).thenReturn(true);

            VirtualFile mockVf = mock(VirtualFile.class);
            stubVirtualFile(attr, mockVf);
            resolver.when(() -> ZulWebRootResolver.findWebRoot(mockVf)).thenReturn(null);

            assertSame(PsiReference.EMPTY_ARRAY,
                    provider.getReferencesByElement(attr, new ProcessingContext()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: No navigation when the path is relative (no leading slash)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void relativePath_noLeadingSlash_returnsEmptyArray() {
        XmlAttributeValue attr = zulAttr("@load('template/item.zul')");

        try (MockedStatic<ZulDomUtil> domUtil = mockStatic(ZulDomUtil.class)) {
            domUtil.when(() -> ZulDomUtil.isZKFile(any(PsiFile.class))).thenReturn(true);

            assertSame(PsiReference.EMPTY_ARRAY,
                    provider.getReferencesByElement(attr, new ProcessingContext()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario Outline: No navigation for annotations other than @load and @init
    //   Examples: @bind, @save, @command, @unknown
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void unrecognisedAnnotations_returnsEmptyArray() {
        String[] expressions = {
                "@bind('/WEB-INF/template/grid.zul')",
                "@save('/WEB-INF/template/grid.zul')",
                "@command('/WEB-INF/template/grid.zul')",
                "@unknown('/WEB-INF/template/grid.zul')"
        };

        for (String expression : expressions) {
            XmlAttributeValue attr = zulAttr(expression);
            try (MockedStatic<ZulDomUtil> domUtil = mockStatic(ZulDomUtil.class)) {
                domUtil.when(() -> ZulDomUtil.isZKFile(any(PsiFile.class))).thenReturn(true);

                assertSame(PsiReference.EMPTY_ARRAY,
                        provider.getReferencesByElement(attr, new ProcessingContext()),
                        "Annotation '" + expression + "' must not produce references");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: No navigation in a non-ZUL XML file
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void nonZulFile_returnsEmptyArray() {
        XmlAttributeValue attr = mock(XmlAttributeValue.class);
        // plain PsiFile (not XmlFile) → real isZKFile returns false
        PsiFile nonXmlFile = mock(PsiFile.class);
        when(attr.getContainingFile()).thenReturn(nonXmlFile);

        assertSame(PsiReference.EMPTY_ARRAY,
                provider.getReferencesByElement(attr, new ProcessingContext()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario: No navigation when the binding expression is empty
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void emptyBindingExpression_returnsEmptyArray() {
        XmlAttributeValue attr = mock(XmlAttributeValue.class);
        XmlFile mockFile = mock(XmlFile.class);
        when(attr.getContainingFile()).thenReturn(mockFile);
        when(attr.getValue()).thenReturn("");

        try (MockedStatic<ZulDomUtil> domUtil = mockStatic(ZulDomUtil.class)) {
            domUtil.when(() -> ZulDomUtil.isZKFile(any(PsiFile.class))).thenReturn(true);

            assertSame(PsiReference.EMPTY_ARRAY,
                    provider.getReferencesByElement(attr, new ProcessingContext()));
        }
    }
}
