package org.zkoss.zkidea.dom;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zkoss.zkidea.lang.ZulSchemaProvider;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ZkDomElementDescriptorProvider} (delegation) and
 * {@link ZkDomElementDescriptorHolder} (file-kind guard and descriptor creation).
 *
 * <p>Feature file: {@code zul-code-completion.feature} — scenarios
 * "ZkDomElementDescriptorProvider returns a non-null descriptor for a &lt;window&gt; tag"
 * and "ZkDomElementDescriptorProvider returns null for a tag in a plain XML file".
 *
 * <h3>Test split:</h3>
 * <ul>
 *   <li>{@code ZkDomElementDescriptorProvider} tests mock the holder entirely —
 *       they verify the delegation contract only.</li>
 *   <li>{@code ZkDomElementDescriptorHolder} tests use a real holder instance with
 *       mocked IntelliJ APIs where needed.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ZkDomElementDescriptorProviderTest {

    private final ZkDomElementDescriptorProvider provider = new ZkDomElementDescriptorProvider();

    // ═══════════════════════════════════════════════════════════════════════════
    // ZkDomElementDescriptorProvider — delegation contract
    // The holder is mocked; only the provider's own code is exercised.
    // (No file-name stubs needed — the holder mock short-circuits the real lookup.)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Feature: "ZkDomElementDescriptorProvider returns null for a tag in a plain XML file".
     * When the holder returns null (file not recognised as ZK), the provider propagates null.
     */
    @Test
    void getDescriptor_returnsNull_whenHolderReturnsNull() {
        Project project = mock(Project.class);
        XmlTag tag = mock(XmlTag.class);
        ZkDomElementDescriptorHolder holderMock = mock(ZkDomElementDescriptorHolder.class);

        when(tag.getProject()).thenReturn(project);
        when(holderMock.getDescriptor(tag)).thenReturn(null);

        try (MockedStatic<ZkDomElementDescriptorHolder> staticHolder =
                     mockStatic(ZkDomElementDescriptorHolder.class)) {
            staticHolder.when(() -> ZkDomElementDescriptorHolder.getInstance(project))
                    .thenReturn(holderMock);

            XmlElementDescriptor result = provider.getDescriptor(tag);

            assertNull(result, "Provider must propagate null from the holder");
        }
    }

    /**
     * Feature: "ZkDomElementDescriptorProvider returns a non-null descriptor for a &lt;window&gt; tag".
     * When the holder returns a descriptor, the provider must return exactly that descriptor.
     */
    @Test
    void getDescriptor_returnsDescriptor_whenHolderProvidesOne() {
        Project project = mock(Project.class);
        XmlTag windowTag = mock(XmlTag.class);
        XmlElementDescriptor expectedDescriptor = mock(XmlElementDescriptor.class);
        ZkDomElementDescriptorHolder holderMock = mock(ZkDomElementDescriptorHolder.class);

        when(windowTag.getProject()).thenReturn(project);
        when(holderMock.getDescriptor(windowTag)).thenReturn(expectedDescriptor);

        try (MockedStatic<ZkDomElementDescriptorHolder> staticHolder =
                     mockStatic(ZkDomElementDescriptorHolder.class)) {
            staticHolder.when(() -> ZkDomElementDescriptorHolder.getInstance(project))
                    .thenReturn(holderMock);

            XmlElementDescriptor result = provider.getDescriptor(windowTag);

            assertNotNull(result,
                    "Provider must return a non-null descriptor when the holder provides one");
            assertSame(expectedDescriptor, result,
                    "Provider must return the exact descriptor object from the holder");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ZkDomElementDescriptorHolder — file-kind guard (no IntelliJ services needed)
    // The holder is a real instance; it returns null early when the file is not a ZK file.
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Feature: "ZkDomElementDescriptorProvider returns null for a tag in a plain XML file".
     * A real holder instance returns null when the containing file is not .zul / zk.xml /
     * lang-addon.xml. This early exit happens before any IntelliJ service is called.
     */
    @Test
    void holder_getDescriptor_returnsNull_forNonZkXmlFile() {
        Project project = mock(Project.class);
        XmlFile plainXmlFile = mock(XmlFile.class);
        XmlTag tag = mock(XmlTag.class);

        when(tag.getContainingFile()).thenReturn(plainXmlFile);
        // A name not matched by isZKFile / isZkConfigFile / isLangAddonFile
        when(plainXmlFile.getName()).thenReturn("pom.xml");

        ZkDomElementDescriptorHolder holder = new ZkDomElementDescriptorHolder(project);
        XmlElementDescriptor result = holder.getDescriptor(tag);

        assertNull(result,
                "Holder must return null for files that are not .zul / zk.xml / lang-addon.xml");
    }

    /**
     * A real holder returns null when the PSI file is not an {@code XmlFile} at all.
     * {@code ZulDomUtil.isZKFile()} first checks {@code instanceof XmlFile}; any other
     * type causes an early null return before IntelliJ services are needed.
     */
    @Test
    void holder_getDescriptor_returnsNull_forNonXmlPsiFile() {
        Project project = mock(Project.class);
        PsiFile nonXmlFile = mock(PsiFile.class); // NOT XmlFile
        XmlTag tag = mock(XmlTag.class);

        when(tag.getContainingFile()).thenReturn(nonXmlFile);

        ZkDomElementDescriptorHolder holder = new ZkDomElementDescriptorHolder(project);
        XmlElementDescriptor result = holder.getDescriptor(tag);

        assertNull(result,
                "Holder must return null when the PSI file is not an XmlFile");
    }

    /**
     * Regression test for cold-start "no suggestion" bug.
     *
     * <p>On first {@code runIde} after {@code clean}, VFS may not have indexed the plugin JAR
     * yet when the first completion is triggered. {@code doCreateDescriptor()} then returns null,
     * and if that null is cached in {@code myDescriptorsMap}, suggestions stay broken for the
     * entire IDE session until a PSI modification invalidates the cache.
     *
     * <p>This test verifies that a null descriptor is NOT permanently cached: the second call
     * must retry {@code doCreateDescriptor()} and return the descriptor once the schema becomes
     * available (e.g., after VFS finishes indexing the JAR).
     */
    @SuppressWarnings("unchecked")
    @Test
    void holder_getDescriptor_retriesAfterNull_untilSchemaBecomesAvailable() {
        Project project = mock(Project.class);
        XmlFile zulFile = mock(XmlFile.class);
        XmlTag tag = mock(XmlTag.class);
        XmlNSDescriptorImpl nsMock = mock(XmlNSDescriptorImpl.class);
        CachedValue<XmlNSDescriptorImpl> nullCachedValue = mock(CachedValue.class);
        CachedValue<XmlNSDescriptorImpl> validCachedValue = mock(CachedValue.class);
        CachedValuesManager cacheManagerMock = mock(CachedValuesManager.class);
        XmlElementDescriptor elementDescriptorMock = mock(XmlElementDescriptor.class);

        when(tag.getContainingFile()).thenReturn(zulFile);
        when(zulFile.getName()).thenReturn("index.zul");
        when(tag.isValid()).thenReturn(true);
        when(tag.getName()).thenReturn("window");
        when(nsMock.isValid()).thenReturn(true);
        when(nsMock.getDefaultNamespace()).thenReturn("http://www.zkoss.org/2005/zul");
        when(nsMock.getElementDescriptor(eq("window"), anyString())).thenReturn(elementDescriptorMock);
        when(nullCachedValue.getValue()).thenReturn(null);   // first attempt: schema not available
        when(validCachedValue.getValue()).thenReturn(nsMock); // second attempt: schema ready
        // First createCachedValue call → null descriptor; second call → valid descriptor
        doReturn(nullCachedValue).doReturn(validCachedValue)
                .when(cacheManagerMock).createCachedValue(any(), anyBoolean());

        try (MockedStatic<CachedValuesManager> cvm = mockStatic(CachedValuesManager.class)) {
            cvm.when(() -> CachedValuesManager.getManager(project)).thenReturn(cacheManagerMock);

            ZkDomElementDescriptorHolder holder = new ZkDomElementDescriptorHolder(project);

            // First call: schema not available yet (VFS not indexed) → null expected
            XmlElementDescriptor first = holder.getDescriptor(tag);
            assertNull(first, "First call must return null when schema is not yet available");

            // Second call: schema is now available → must NOT return cached null, must retry
            XmlElementDescriptor second = holder.getDescriptor(tag);
            assertNotNull(second,
                    "Second call must return a non-null descriptor after schema becomes available. "
                            + "Permanently caching null breaks suggestions for the entire IDE session.");
            // Note: result is wrapped in ZulChildCompletionDescriptor; assertSame on mock not applicable.
        }
    }

    /**
     * Regression test for cold-start "no suggestion" bug caused by stale ExternalResourceManager state.
     *
     * <p>Root cause: {@code clean} only deletes the {@code build/} directory, NOT the sandbox
     * ({@code .sandbox/config/}). The sandbox persists schema mappings across runs. If the user
     * previously ran the plugin with a different project (e.g., {@code SUPPORT/plugin-test}),
     * that wrong path remains in {@code ExternalResourceManager} when the next {@code runIde}
     * starts. {@code ZKProjectsManager.updateZulSchema()} fixes it via {@code invokeLater}, but
     * only after Maven initializes — which can be many seconds into the session.
     *
     * <p>This test verifies that {@code doCreateDescriptor()} falls back to the plugin's
     * classpath (the JAR always has the XSD) when {@code ExternalResourceManager} returns a
     * stale path that does not resolve to a valid schema file.
     */
    @Test
    void doCreateDescriptor_usesClasspathFallback_whenExternalResourceManagerHasStalePath() {
        Project project = mock(Project.class);
        ExternalResourceManager ermMock = mock(ExternalResourceManager.class);
        VirtualFile correctSchemaVF = mock(VirtualFile.class);

        // Stale path from a previous session in a different project — not an XSD file path
        when(ermMock.getResourceLocation(ZulSchemaProvider.ZUL_PROJECT_SCHEMA_URL, ""))
                .thenReturn("/Users/hawk/Documents/workspace/SUPPORT/plugin-test");

        ZkDomElementDescriptorHolder holder = new ZkDomElementDescriptorHolder(project);

        try (MockedStatic<ExternalResourceManager> ermStatic = mockStatic(ExternalResourceManager.class);
             MockedStatic<VfsUtil> vfsStatic = mockStatic(VfsUtil.class)) {

            ermStatic.when(ExternalResourceManager::getInstance).thenReturn(ermMock);
            // The stale path "/Users/hawk/.../plugin-test" has no "file:" prefix, so
            // URI.create() throws IllegalArgumentException (URI is not absolute).
            // Only the classpath URL reaches VfsUtil.findFileByURL().
            vfsStatic.when(() -> VfsUtil.findFileByURL(any(URL.class))).thenReturn(correctSchemaVF);

            VirtualFile result =
                    holder.findSchemaFile(ZkDomElementDescriptorHolder.FileKind.ZUL_FILE);

            assertNotNull(result,
                    "Classpath fallback must provide the schema VirtualFile when "
                            + "ExternalResourceManager holds a stale path from a previous session.");
            assertSame(correctSchemaVF, result);
        }
    }

    /**
     * Regression test for context-sensitive child completion inside &lt;listbox&gt;.
     *
     * <p>Bug: typing "&lt;" inside &lt;listbox&gt; shows ALL ZK components instead of only
     * elements permitted by {@code listboxType} in zul.xsd:
     * {@code listitem}, {@code listhead}, {@code listgroup}, {@code listgroupfoot},
     * {@code frozen}, {@code auxhead}, and {@code baseGroup} items.
     *
     * <p>The holder must return the XSD-based {@link XmlElementDescriptor} for the
     * &lt;listbox&gt; tag so that IntelliJ can call its
     * {@code getElementsDescriptors(XmlTag context)} and get schema-constrained children.
     * If the holder returns a descriptor that ignores the parent element's type
     * (i.e., returns all top-level elements from the schema), the completion list will
     * show unrelated components like {@code window}, {@code button}, {@code grid}, etc.
     *
     * <p>Feature file: {@code zul-code-completion.feature} — scenarios
     * "Child completion inside &lt;listbox&gt; shows only schema-valid children".
     */
    @SuppressWarnings("unchecked")
    @Test
    void holder_getDescriptor_forListbox_returnsSchemaDescriptorWithConstrainedChildren() {
        Project project = mock(Project.class);
        XmlFile zulFile = mock(XmlFile.class);
        XmlTag listboxTag = mock(XmlTag.class);
        XmlNSDescriptorImpl nsMock = mock(XmlNSDescriptorImpl.class);
        CachedValue<XmlNSDescriptorImpl> cachedValueMock = mock(CachedValue.class);
        CachedValuesManager cacheManagerMock = mock(CachedValuesManager.class);

        // Descriptors for valid listbox children only
        XmlElementDescriptor listitemDesc = mock(XmlElementDescriptor.class);
        XmlElementDescriptor listheadDesc = mock(XmlElementDescriptor.class);
        XmlElementDescriptor listgroupDesc = mock(XmlElementDescriptor.class);
        XmlElementDescriptor listgroupfootDesc = mock(XmlElementDescriptor.class);
        XmlElementDescriptor frozenDesc = mock(XmlElementDescriptor.class);
        XmlElementDescriptor auxheadDesc = mock(XmlElementDescriptor.class);
        when(listitemDesc.getName()).thenReturn("listitem");
        when(listheadDesc.getName()).thenReturn("listhead");
        when(listgroupDesc.getName()).thenReturn("listgroup");
        when(listgroupfootDesc.getName()).thenReturn("listgroupfoot");
        when(frozenDesc.getName()).thenReturn("frozen");
        when(auxheadDesc.getName()).thenReturn("auxhead");

        // The XSD-based descriptor for <listbox> knows its allowed children.
        // ZulChildCompletionDescriptor calls delegate.getElementsDescriptors(null) to bypass the
        // xs:any ##other expansion bug, so the mock must be set up with a null context.
        XmlElementDescriptor listboxDescriptor = mock(XmlElementDescriptor.class);
        when(listboxDescriptor.getElementsDescriptors(null)).thenReturn(new XmlElementDescriptor[]{
                listitemDesc, listheadDesc, listgroupDesc, listgroupfootDesc, frozenDesc, auxheadDesc
        });

        when(listboxTag.getContainingFile()).thenReturn(zulFile);
        when(zulFile.getName()).thenReturn("index.zul");
        when(listboxTag.isValid()).thenReturn(true);
        when(listboxTag.getName()).thenReturn("listbox");
        when(nsMock.isValid()).thenReturn(true);
        when(nsMock.getDefaultNamespace()).thenReturn("http://www.zkoss.org/2005/zul");
        when(nsMock.getElementDescriptor(eq("listbox"), anyString())).thenReturn(listboxDescriptor);
        when(cachedValueMock.getValue()).thenReturn(nsMock);
        doReturn(cachedValueMock).when(cacheManagerMock).createCachedValue(any(), anyBoolean());

        try (MockedStatic<CachedValuesManager> cvm = mockStatic(CachedValuesManager.class)) {
            cvm.when(() -> CachedValuesManager.getManager(project)).thenReturn(cacheManagerMock);

            ZkDomElementDescriptorHolder holder = new ZkDomElementDescriptorHolder(project);
            XmlElementDescriptor result = holder.getDescriptor(listboxTag);

            assertNotNull(result,
                    "Holder must return a non-null descriptor for <listbox> in a .zul file");
            // result is wrapped in ZulChildCompletionDescriptor to fix the xs:any expansion bug;
            // identity check on the underlying mock is not applicable.

            // Verify the descriptor supplies only schema-valid children — not all ZK components
            XmlElementDescriptor[] children = result.getElementsDescriptors(listboxTag);
            java.util.Set<String> childNames = new java.util.HashSet<>();
            for (XmlElementDescriptor child : children) {
                childNames.add(child.getName());
            }

            assertTrue(childNames.contains("listitem"),   "listitem must be a valid child of <listbox>");
            assertTrue(childNames.contains("listhead"),   "listhead must be a valid child of <listbox>");
            assertTrue(childNames.contains("listgroup"),  "listgroup must be a valid child of <listbox>");
            assertTrue(childNames.contains("listgroupfoot"), "listgroupfoot must be a valid child of <listbox>");
            assertTrue(childNames.contains("frozen"),     "frozen must be a valid child of <listbox>");
            assertTrue(childNames.contains("auxhead"),    "auxhead must be a valid child of <listbox>");

            assertFalse(childNames.contains("window"),
                    "window must NOT appear as a child of <listbox> — bug: all components shown");
            assertFalse(childNames.contains("button"),
                    "button must NOT appear as a child of <listbox> — bug: all components shown");
            assertFalse(childNames.contains("grid"),
                    "grid must NOT appear as a child of <listbox> — bug: all components shown");
            assertFalse(childNames.contains("textbox"),
                    "textbox must NOT appear as a child of <listbox> — bug: all components shown");
        }
    }

    /**
     * Feature: "ZkDomElementDescriptorProvider returns a non-null descriptor for a &lt;window&gt; tag".
     *
     * <p>A real {@link ZkDomElementDescriptorHolder} must return a non-null descriptor for a
     * tag in a {@code .zul} file. {@code CachedValuesManager} and {@code XmlNSDescriptorImpl}
     * are mocked so this test runs outside a live IntelliJ application context.
     *
     * <p>The test exercises the full delegation chain:
     * {@code getDescriptor()} → {@code tryGetOrCreateDescriptor()} → cached value → element
     * descriptor. It confirms the holder correctly delegates to the cached descriptor and does
     * not short-circuit to null due to a missing service or a URL-parsing exception.
     */
    @SuppressWarnings("unchecked")
    @Test
    void holder_getDescriptor_returnsNonNull_forZulTag() {
        Project project = mock(Project.class);
        XmlFile zulFile = mock(XmlFile.class);
        XmlTag tag = mock(XmlTag.class);
        XmlNSDescriptorImpl nsMock = mock(XmlNSDescriptorImpl.class);
        CachedValue<XmlNSDescriptorImpl> cachedValueMock = mock(CachedValue.class);
        CachedValuesManager cacheManagerMock = mock(CachedValuesManager.class);
        XmlElementDescriptor elementDescriptorMock = mock(XmlElementDescriptor.class);

        when(tag.getContainingFile()).thenReturn(zulFile);
        when(zulFile.getName()).thenReturn("index.zul");   // passes isZKFile() guard
        when(tag.isValid()).thenReturn(true);
        when(tag.getName()).thenReturn("window");
        when(nsMock.isValid()).thenReturn(true);
        when(nsMock.getDefaultNamespace()).thenReturn("http://www.zkoss.org/2005/zul");
        when(nsMock.getElementDescriptor(eq("window"), anyString())).thenReturn(elementDescriptorMock);
        when(cachedValueMock.getValue()).thenReturn(nsMock);
        // doReturn avoids the generic type mismatch that when().thenReturn() triggers here
        doReturn(cachedValueMock).when(cacheManagerMock).createCachedValue(any(), anyBoolean());

        try (MockedStatic<CachedValuesManager> cvm = mockStatic(CachedValuesManager.class)) {
            cvm.when(() -> CachedValuesManager.getManager(project)).thenReturn(cacheManagerMock);

            ZkDomElementDescriptorHolder holder = new ZkDomElementDescriptorHolder(project);
            XmlElementDescriptor result = holder.getDescriptor(tag);

            assertNotNull(result,
                    "ZkDomElementDescriptorHolder must return a non-null descriptor for a .zul tag. "
                            + "'No suggestion' regression occurs when this returns null.");
            // result is wrapped in ZulChildCompletionDescriptor; identity check on mock not applicable.
        }
    }
}
