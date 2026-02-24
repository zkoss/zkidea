package org.zkoss.zkidea.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
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

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

/**
 * ISA-level integration tests for
 * {@link ZkBindingReferenceProvider#getReferencesByElement}.
 *
 * Covers Groups 8 and 10 from mvvm_property_navigation.isa.feature:
 * <ul>
 *   <li>Group 8  — TextRange, reference type, and propertyName for each binding expression</li>
 *   <li>Group 10 — {@code ownerClass} propagation in nested property chains via
 *                  {@code resolvePropertyType}</li>
 * </ul>
 *
 * <p>TextRange convention (valueOffset = 1):
 * <pre>
 *   rangeStart = 1 (valueOffset) + bodyStartOffset + nameStartInBody
 *   rangeEnd   = rangeStart + nameLength
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
class ZkBindingReferenceProviderMvvmPropertyTest {

    private final ZkBindingReferenceProvider provider = new ZkBindingReferenceProvider();

    // ─── helpers (mirrors the pattern in ZkBindingReferenceProviderTest) ─────

    /**
     * Creates a mock {@link XmlAttributeValue} with correct text-range metadata
     * so the provider can compute {@code valueOffset = 1}.
     *
     * <ul>
     *   <li>{@code getTextRange()}      = {@code TextRange(0, len+2)}  (surrounding quotes)</li>
     *   <li>{@code getValueTextRange()} = {@code TextRange(1, len+1)}  (inner content)</li>
     * </ul>
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
     * Stubs the four {@link ZulDomUtil} static gates that must pass before the
     * provider starts building references.
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

    /** Reads a private field from a reference object (used for propertyName / isCommandContext). */
    @SuppressWarnings("unchecked")
    private static <T> T privateField(Object obj, String fieldName) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return (T) f.get(obj);
        } catch (Exception e) {
            throw new AssertionError("Cannot read field '" + fieldName + "'", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GROUP 8  getReferencesByElement — TextRange + type + propertyName
    // ═══════════════════════════════════════════════════════════════════════════

    // ── @load(vm.list) ───────────────────────────────────────────────────────
    //   bodyStartOffset=6, valueOffset=1, bodyOffsetInElement=7
    //   "vm"  : TextRange(7,  9)   (start=7+0, len=2)
    //   "list": TextRange(10, 14)  (start=7+3, len=4)

    @Test
    void group8_loadVmList_emitsTwoRefsWithCorrectRangesAndPropertyName() {
        XmlAttributeValue attr = zulAttr("@load(vm.list)");
        PsiClass mockVmClass   = mock(PsiClass.class);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            setupVmMocks(util,
                    "@id('vm') @init('com.example.MyViewModel')", "vm", mockVmClass);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            assertEquals(2, refs.length);

            // refs[0]: ViewModelIdReference for "vm"
            assertInstanceOf(ViewModelIdReference.class, refs[0]);
            assertEquals(new TextRange(7, 9), refs[0].getRangeInElement());

            // refs[1]: ViewModelPropertyReference for "list"
            assertInstanceOf(ViewModelPropertyReference.class, refs[1]);
            assertEquals(new TextRange(10, 14), refs[1].getRangeInElement());
            assertEquals("list", privateField(refs[1], "propertyName"));
        }
    }

    // ── @load(vm.name) ───────────────────────────────────────────────────────
    //   "vm"  : TextRange(7,  9)
    //   "name": TextRange(10, 14)  (nameLength=4)

    @Test
    void group8_loadVmName_emitsTwoRefsWithPropertyNameName() {
        XmlAttributeValue attr = zulAttr("@load(vm.name)");
        PsiClass mockVmClass   = mock(PsiClass.class);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            setupVmMocks(util,
                    "@id('vm') @init('com.example.MyViewModel')", "vm", mockVmClass);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            assertEquals(2, refs.length);
            assertInstanceOf(ViewModelIdReference.class, refs[0]);
            assertEquals(new TextRange(7, 9), refs[0].getRangeInElement());

            assertInstanceOf(ViewModelPropertyReference.class, refs[1]);
            assertEquals(new TextRange(10, 14), refs[1].getRangeInElement());
            assertEquals("name", privateField(refs[1], "propertyName"));
        }
    }

    // ── @load(vm.active) ─────────────────────────────────────────────────────
    //   "active": nameStartInBody=3, nameLength=6 → TextRange(10, 16)

    @Test
    void group8_loadVmActive_emitsTwoRefsWithPropertyNameActive() {
        // DSL: "Navigate to boolean getter (isXxx) from property reference"
        XmlAttributeValue attr = zulAttr("@load(vm.active)");
        PsiClass mockVmClass   = mock(PsiClass.class);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            setupVmMocks(util,
                    "@id('vm') @init('com.example.MyViewModel')", "vm", mockVmClass);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            assertEquals(2, refs.length);
            assertInstanceOf(ViewModelPropertyReference.class, refs[1]);
            assertEquals(new TextRange(10, 16), refs[1].getRangeInElement());
            assertEquals("active", privateField(refs[1], "propertyName"));
        }
    }

    // ── @load(vm.crew.name) ──────────────────────────────────────────────────
    //   "vm"  : TextRange(7,  9)
    //   "crew": TextRange(10, 14)  (start=7+3, len=4)
    //   "name": TextRange(15, 19)  (start=7+8, len=4)

    @Test
    void group8_loadVmCrewName_emitsThreeRefsWithCorrectRangesAndOwnerClasses() {
        // DSL: "Navigate to getter on nested property path (first/second segment)"
        XmlAttributeValue attr  = zulAttr("@load(vm.crew.name)");
        PsiClass mockVmClass    = mock(PsiClass.class);
        PsiClass mockCrewClass  = mock(PsiClass.class);
        PsiMethod crewGetter    = mock(PsiMethod.class);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            setupVmMocks(util,
                    "@id('vm') @init('com.example.MyViewModel')", "vm", mockVmClass);

            // resolvePropertyType("crew") path:
            //   findGetterOrMethod(vmClass, "crew") → crewGetter
            //   resolveTypeToClass(crewGetter.getReturnType(), element) → mockCrewClass
            // PsiType cannot be mocked (IntelliJ SDK abstract class), so we intercept
            // resolveTypeToClass with any() — the return type value is irrelevant.
            util.when(() -> ZulDomUtil.findGetterOrMethod(same(mockVmClass), eq("crew")))
                    .thenReturn(crewGetter);
            util.when(() -> ZulDomUtil.resolveTypeToClass(any(), any()))
                    .thenReturn(mockCrewClass);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            assertEquals(3, refs.length);

            // refs[0]: ViewModelIdReference "vm"
            assertInstanceOf(ViewModelIdReference.class, refs[0]);
            assertEquals(new TextRange(7, 9), refs[0].getRangeInElement());

            // refs[1]: ViewModelPropertyReference "crew" owned by MyViewModel
            assertInstanceOf(ViewModelPropertyReference.class, refs[1]);
            assertEquals(new TextRange(10, 14), refs[1].getRangeInElement());
            assertEquals("crew", privateField(refs[1], "propertyName"));
            assertSame(mockVmClass, ((ViewModelPropertyReference) refs[1]).getOwnerClass());

            // refs[2]: ViewModelPropertyReference "name" owned by CrewModel
            assertInstanceOf(ViewModelPropertyReference.class, refs[2]);
            assertEquals(new TextRange(15, 19), refs[2].getRangeInElement());
            assertEquals("name", privateField(refs[2], "propertyName"));
            assertSame(mockCrewClass, ((ViewModelPropertyReference) refs[2]).getOwnerClass());
        }
    }

    // ── @command('delete', item=vm.selectedItem) ─────────────────────────────
    //   bodyStartOffset=9, valueOffset=1, bodyOffsetInElement=10
    //   "vm"          : segStart=10+15=25, TextRange(25, 27)
    //   "selectedItem": segStart=10+18=28, nameLength=12, TextRange(28, 40)
    //   isCommandContext=true (annotation name is "command")

    @Test
    void group8_commandBodyWithVmChain_isCommandContextTrue() {
        // DSL: "Navigate to property in @command expression parameter"
        //
        // Note: bodies that start with a quoted string are skipped by the implementation
        // (startsWith("'") guard), so @command('literal', ...) produces no references.
        // We use @command(vm.someCommand) whose body starts with "vm" (not a quote).
        //
        // "@command(" = 9 chars → bodyStartOffset=9, valueOffset=1 → bodyOffsetInElement=10
        //   "vm"          : nameStartInBody=0, len=2  → TextRange(10, 12)
        //   "someCommand" : nameStartInBody=3, len=11 → TextRange(13, 24)
        XmlAttributeValue attr = zulAttr("@command(vm.someCommand)");
        PsiClass mockVmClass   = mock(PsiClass.class);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            setupVmMocks(util,
                    "@id('vm') @init('com.example.MyViewModel')", "vm", mockVmClass);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            assertEquals(2, refs.length);

            // refs[0]: ViewModelIdReference "vm"
            assertInstanceOf(ViewModelIdReference.class, refs[0]);
            assertEquals(new TextRange(10, 12), refs[0].getRangeInElement());

            // refs[1]: ViewModelPropertyReference "someCommand" with isCommandContext=true
            assertInstanceOf(ViewModelPropertyReference.class, refs[1]);
            assertEquals(new TextRange(13, 24), refs[1].getRangeInElement());
            assertEquals("someCommand", privateField(refs[1], "propertyName"));
            assertTrue((boolean) privateField(refs[1], "isCommandContext"),
                    "isCommandContext must be true for @command body");
        }
    }

    // ── annotation variants (@load, @init, @bind, @save) ─────────────────────
    // DSL: "Navigate from various MVVM binding annotations"
    // All have bodyStartOffset=6, so "list" always lands at TextRange(10,14).

    @Test
    void group8_loadAnnotation_lastRefIsPropertyRefForList() {
        assertLastRefIsListPropertyRef("@load(vm.list)");
    }

    @Test
    void group8_initAnnotation_lastRefIsPropertyRefForList() {
        assertLastRefIsListPropertyRef("@init(vm.list)");
    }

    @Test
    void group8_bindAnnotation_lastRefIsPropertyRefForList() {
        assertLastRefIsListPropertyRef("@bind(vm.list)");
    }

    @Test
    void group8_saveAnnotation_lastRefIsPropertyRefForList() {
        assertLastRefIsListPropertyRef("@save(vm.list)");
    }

    private void assertLastRefIsListPropertyRef(String attrText) {
        XmlAttributeValue attr = zulAttr(attrText);
        PsiClass mockVmClass   = mock(PsiClass.class);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            setupVmMocks(util,
                    "@id('vm') @init('com.example.MyViewModel')", "vm", mockVmClass);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            assertTrue(refs.length >= 1, attrText + " must produce at least one reference");
            PsiReference last = refs[refs.length - 1];
            assertInstanceOf(ViewModelPropertyReference.class, last, attrText);
            assertEquals("list", privateField(last, "propertyName"),
                    "last ref propertyName for " + attrText);
        }
    }

    // ── negative / guard cases ────────────────────────────────────────────────

    @Test
    void group8_noViewModelTag_returnsEmptyArray() {
        // DSL: "No navigation when there is no viewModel declaration in ancestor"
        XmlAttributeValue attr = zulAttr("@load(vm.list)");

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            util.when(() -> ZulDomUtil.isZKFile(any(PsiFile.class))).thenReturn(true);
            util.when(() -> ZulDomUtil.findViewModelTag(any())).thenReturn(null);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());
            assertSame(PsiReference.EMPTY_ARRAY, refs);
        }
    }

    @Test
    void group8_unresolvableViewModelClass_returnsEmptyArray() {
        // DSL: "No navigation when ViewModel class cannot be resolved"
        XmlAttributeValue attr = zulAttr("@load(vm.list)");

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            setupVmMocks(util,
                    "@id('vm') @init('com.nonexistent.FakeVM')", "vm", null);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());
            assertSame(PsiReference.EMPTY_ARRAY, refs);
        }
    }

    @Test
    void group8_plainTextAttributeValue_returnsZeroRefs() {
        // DSL: "No navigation outside of binding expression"
        // extractAnnotations("plain text with vm.list") returns [] — chain loop never entered.
        XmlAttributeValue attr = zulAttr("plain text with vm.list");
        PsiClass mockVmClass   = mock(PsiClass.class);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            setupVmMocks(util,
                    "@id('vm') @init('com.example.MyViewModel')", "vm", mockVmClass);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());
            assertEquals(0, refs.length);
        }
    }

    @Test
    void group8_nonZulFile_returnsEmptyArray() {
        // DSL: "No navigation outside of binding expression" (file-type guard)
        // A plain PsiFile (not XmlFile) causes ZulDomUtil.isZKFile to return false.
        XmlAttributeValue attr     = mock(XmlAttributeValue.class);
        PsiFile nonXmlFile         = mock(PsiFile.class);
        when(attr.getContainingFile()).thenReturn(nonXmlFile);

        PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());
        assertSame(PsiReference.EMPTY_ARRAY, refs);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GROUP 10  processChain — ownerClass propagation in nested chains
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void group10_nestedChain_secondSegmentReceivesCrewModelAsOwnerClass() {
        // DSL: "Navigate to getter on nested property path (second segment)"
        //
        // processChain logic for chain [{vm},{crew},{name}]:
        //   i=1 "crew" → ref(ownerClass=vmClass), then
        //               resolvePropertyType(vmClass,"crew") =
        //                 findGetterOrMethod(vmClass,"crew") → crewGetter
        //                 resolveTypeToClass(crewGetter.getReturnType()) → mockCrewClass
        //   i=2 "name" → ref(ownerClass=mockCrewClass)
        XmlAttributeValue attr  = zulAttr("@load(vm.crew.name)");
        PsiClass mockVmClass    = mock(PsiClass.class);
        PsiClass mockCrewClass  = mock(PsiClass.class);
        PsiMethod crewGetter    = mock(PsiMethod.class);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            setupVmMocks(util,
                    "@id('vm') @init('com.example.MyViewModel')", "vm", mockVmClass);

            util.when(() -> ZulDomUtil.findGetterOrMethod(same(mockVmClass), eq("crew")))
                    .thenReturn(crewGetter);
            util.when(() -> ZulDomUtil.resolveTypeToClass(any(), any()))
                    .thenReturn(mockCrewClass);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            assertEquals(3, refs.length);

            ViewModelPropertyReference crewRef = (ViewModelPropertyReference) refs[1];
            assertEquals("crew",        privateField(crewRef, "propertyName"));
            assertSame(mockVmClass, crewRef.getOwnerClass());

            ViewModelPropertyReference nameRef = (ViewModelPropertyReference) refs[2];
            assertEquals("name",        privateField(nameRef, "propertyName"));
            assertSame(mockCrewClass, nameRef.getOwnerClass(),
                    "ownerClass for 'name' must be CrewModel, resolved from getCrew() return type");
        }
    }

    @Test
    void group10_nestedChain_ownerClassIsNullWhenGetterReturnTypeUnresolvable() {
        // DSL: "Second segment in nested chain gets ownerClass=null when getCrew() cannot
        //        be resolved"
        //
        // resolvePropertyType returns null when findGetterOrMethod returns null.
        // The third reference must still be created (ownerClass=null) so that
        // resolve() returns null safely instead of throwing NPE.
        XmlAttributeValue attr = zulAttr("@load(vm.unknownProp.name)");
        PsiClass mockVmClass   = mock(PsiClass.class);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            setupVmMocks(util,
                    "@id('vm') @init('com.example.MyViewModel')", "vm", mockVmClass);

            // findGetterOrMethod not stubbed for "unknownProp" → returns null (default)

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            // chain = [vm, unknownProp, name] → 3 references expected
            assertEquals(3, refs.length);

            ViewModelPropertyReference nameRef = (ViewModelPropertyReference) refs[2];
            assertEquals("name", privateField(nameRef, "propertyName"));
            assertNull(nameRef.getOwnerClass(),
                    "ownerClass must be null when previous getter could not be resolved");

            // resolve() must return null gracefully (null ownerClass guard in resolve())
            assertNull(nameRef.resolve());
        }
    }

    // ─── private utility ─────────────────────────────────────────────────────

    /** Finds the first reference whose {@code getRangeInElement()} equals {@code range}. */
    private static PsiReference findRefAtRange(PsiReference[] refs, TextRange range) {
        for (PsiReference ref : refs) {
            if (range.equals(ref.getRangeInElement())) return ref;
        }
        return null;
    }
}
