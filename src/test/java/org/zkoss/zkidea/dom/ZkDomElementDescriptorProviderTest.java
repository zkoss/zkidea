package org.zkoss.zkidea.dom;

import com.intellij.openapi.project.Project;
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
            assertSame(elementDescriptorMock, result,
                    "Holder must return the exact descriptor object from the cached NS descriptor.");
        }
    }
}
