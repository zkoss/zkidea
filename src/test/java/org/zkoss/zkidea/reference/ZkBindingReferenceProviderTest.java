package org.zkoss.zkidea.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zkoss.zkidea.dom.ZulDomUtil;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ZkBindingReferenceProvider#getReferencesByElement}.
 *
 * <p>Mapped to the scenarios in spec/features/viewmodel-id-navigation.feature.
 *
 * <p>Strategy:
 * <ul>
 *   <li>{@code mockStatic(ZulDomUtil.class)} stubs all static methods that touch
 *       IntelliJ infrastructure ({@code findViewModelTag}, {@code resolveViewModelClass})
 *       and those that need to return controlled values ({@code isZKFile},
 *       {@code extractViewModelId}) so tests are fully deterministic.</li>
 *   <li>PSI interfaces ({@code XmlAttributeValue}, {@code XmlFile}, {@code XmlTag},
 *       {@code PsiClass}) are regular Mockito mocks — they are pure interfaces with no
 *       IntelliJ platform initialization needed.</li>
 *   <li>Rule 1 (non-ZUL file) invokes the real {@code ZulDomUtil.isZKFile(PsiFile)}
 *       directly: a plain {@code PsiFile} mock (not {@code XmlFile}) naturally returns
 *       false, exercising the real file-type guard without any static mocking.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ZkBindingReferenceProviderTest {

    private final ZkBindingReferenceProvider provider = new ZkBindingReferenceProvider();

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a mock XmlAttributeValue whose file and text-range are wired up so
     * that the provider can compute its {@code valueOffset} and read the attribute
     * value string.
     *
     * <p>Text model: element text = {@code "value"} (surrounding quotes counted),
     * so {@code getTextRange()} = [0, len+2) and {@code getValueTextRange()} = [1, len+1).
     * This gives {@code valueOffset = 1}.
     *
     * <p>The {@link XmlFile} mock returned by {@code getContainingFile()} is kept
     * as a field on the {@code attr} mock so it can be passed to
     * {@code ZulDomUtil.isZKFile} stubs in {@link #setupVmMocks}.
     */
    private XmlAttributeValue zulAttr(String value) {
        XmlAttributeValue attr = mock(XmlAttributeValue.class);
        XmlFile mockFile = mock(XmlFile.class);
        when(attr.getContainingFile()).thenReturn(mockFile);
        // lenient: these stubs are unused when getReferencesByElement returns early
        // (e.g. vmClass == null) before reaching the chain-processing code.
        lenient().when(attr.getValue()).thenReturn(value);
        lenient().when(attr.getTextRange())
                .thenReturn(new TextRange(0, value.length() + 2));
        lenient().when(attr.getValueTextRange())
                .thenReturn(new TextRange(1, value.length() + 1));
        return attr;
    }

    /**
     * Stubs all {@link ZulDomUtil} static calls that {@code getReferencesByElement}
     * invokes before chain processing.
     *
     * <ul>
     *   <li>{@code isZKFile(any PsiFile)} → {@code true}</li>
     *   <li>{@code findViewModelTag(any)} → a mock {@link XmlTag} whose
     *       {@code viewModel} attribute returns {@code vmAttrValue}</li>
     *   <li>{@code extractViewModelId(any)} → {@code vmId}</li>
     *   <li>{@code resolveViewModelClass(any, any)} → {@code vmClass} (may be null)</li>
     * </ul>
     */
    private void setupVmMocks(MockedStatic<ZulDomUtil> util,
                              String vmAttrValue,
                              String vmId,
                              PsiClass vmClass) {
        XmlTag mockTag = mock(XmlTag.class);
        when(mockTag.getAttributeValue(ZulDomUtil.VIEW_MODEL)).thenReturn(vmAttrValue);

        util.when(() -> ZulDomUtil.isZKFile(any(PsiFile.class))).thenReturn(true);
        util.when(() -> ZulDomUtil.findViewModelTag(any())).thenReturn(mockTag);
        util.when(() -> ZulDomUtil.extractViewModelId(any())).thenReturn(vmId);
        util.when(() -> ZulDomUtil.resolveViewModelClass(any(), any())).thenReturn(vmClass);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rule 1: Navigation is only available when the file is a .zul file
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Spec: "Ctrl+Click on a binding-like expression in a non-ZUL file does not
     * trigger ViewModel navigation"
     *
     * A {@code PsiFile} that is not an {@code XmlFile} fails the real
     * {@code isZKFile(PsiFile)} check ({@code instanceof XmlFile} is false),
     * so {@code EMPTY_ARRAY} is returned immediately — no static mocking needed.
     */
    @Test
    void rule1_nonZulFile_returnsEmptyArray() {
        XmlAttributeValue attr = mock(XmlAttributeValue.class);
        // plain PsiFile mock: NOT an XmlFile → real isZKFile(PsiFile) returns false
        PsiFile nonXmlFile = mock(PsiFile.class);
        when(attr.getContainingFile()).thenReturn(nonXmlFile);

        PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

        assertSame(PsiReference.EMPTY_ARRAY, refs,
                "Non-ZUL file must produce no references");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rule 2: Only the first identifier segment triggers ViewModel navigation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Spec: "Ctrl+Click on a non-root segment does not trigger ViewModel class navigation"
     *
     * For {@code @load(vm.user.name)} the provider emits:
     * <ol>
     *   <li>A {@link ViewModelIdReference} for "vm" (root, matches @id alias).</li>
     *   <li>A {@link ViewModelPropertyReference} for "user" (property segment).</li>
     *   <li>A {@link ViewModelPropertyReference} for "name" (property segment).</li>
     * </ol>
     * The reference at the "user" position must NOT be a {@link ViewModelIdReference}.
     */
    @Test
    void rule2_nonRootSegment_noViewModelIdReferenceForPropertySegment() {
        XmlAttributeValue attr = zulAttr("@load(vm.user.name)");
        PsiClass mockVmClass = mock(PsiClass.class);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            setupVmMocks(util,
                    "@id('vm') @init('com.example.UserViewModel')", "vm", mockVmClass);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            // Exactly one ViewModelIdReference — only "vm" qualifies
            long idRefCount = Arrays.stream(refs)
                    .filter(r -> r instanceof ViewModelIdReference)
                    .count();
            assertEquals(1, idRefCount,
                    "Only the root 'vm' segment should be a ViewModelIdReference");

            // The second reference (position of "user") must be a property reference
            assertTrue(refs.length > 1, "Expected more than one reference in the chain");
            assertFalse(refs[1] instanceof ViewModelIdReference,
                    "The 'user' segment must not be a ViewModelIdReference");
            assertInstanceOf(ViewModelPropertyReference.class, refs[1],
                    "The 'user' segment must resolve as a ViewModelPropertyReference");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rule 3: First chain segment must match the @id alias
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Spec: "Ctrl+Click on a template variable root does not trigger ViewModel navigation"
     *
     * For {@code @load(item.name)} with vmId="vm", the chain root "item" does not
     * match the @id alias. {@code processChain} skips the whole chain → no references.
     */
    @Test
    void rule3_rootSegmentNotMatchingVmId_returnsEmptyArray() {
        XmlAttributeValue attr = zulAttr("@load(item.name)");
        PsiClass mockVmClass = mock(PsiClass.class);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            setupVmMocks(util,
                    "@id('vm') @init('com.example.UserViewModel')", "vm", mockVmClass);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            assertEquals(0, refs.length,
                    "'item' does not match vmId 'vm' — no references should be created");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rule 4: Unresolvable ViewModel class → no navigation, no error
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Spec: "Unresolvable ViewModel class results in a silent no-op"
     *
     * When {@code resolveViewModelClass} returns {@code null} (class not on classpath),
     * the provider returns {@code EMPTY_ARRAY} without throwing any exception.
     */
    @Test
    void rule4_unresolvableViewModelClass_returnsEmptyArraySilently() {
        XmlAttributeValue attr = zulAttr("@load(vm.userName)");

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            // null vmClass simulates resolveViewModelClass returning null
            setupVmMocks(util,
                    "@id('vm') @init('com.example.MissingViewModel')", "vm", null);

            PsiReference[] refs = assertDoesNotThrow(
                    () -> provider.getReferencesByElement(attr, new ProcessingContext()),
                    "Unresolvable class must not throw an exception");

            assertSame(PsiReference.EMPTY_ARRAY, refs,
                    "Unresolvable class must produce no references");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Happy path — validates that all gates pass when conditions are met
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * When all gate conditions are satisfied (.zul file, viewModel tag with both
     * @id and @init, root segment matches @id, class resolvable), the first reference
     * must be a {@link ViewModelIdReference} that resolves to the ViewModel class.
     */
    @Test
    void happyPath_allConditionsMet_firstRefIsViewModelIdReference() {
        String vmFqn = MyViewModel.class.getName(); // "org.zkoss.zkidea.reference.MyViewModel"
        XmlAttributeValue attr = zulAttr("@load(vm.userName)");

        PsiClass mockVmClass = mock(PsiClass.class);
        when(mockVmClass.getQualifiedName()).thenReturn(vmFqn);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            setupVmMocks(util,
                    "@id('vm') @init('" + vmFqn + "')", "vm", mockVmClass);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            assertTrue(refs.length >= 1, "Expected at least one reference");
            assertInstanceOf(ViewModelIdReference.class, refs[0],
                    "First reference must be a ViewModelIdReference for the 'vm' alias");

            ViewModelIdReference idRef = (ViewModelIdReference) refs[0];
            assertSame(mockVmClass, idRef.resolve(),
                    "ViewModelIdReference must resolve to the MyViewModel PsiClass");
            assertEquals(vmFqn, ((PsiClass) idRef.resolve()).getQualifiedName(),
                    "Resolved class must be MyViewModel");
        }
    }
}
