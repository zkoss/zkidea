package org.zkoss.zkidea.dom;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ISA-level unit tests for {@link ZulDomUtil} static utilities.
 * Covers Groups 1, 2, 3, and 9 from mvvm_property_navigation.isa.feature.
 *
 * <ul>
 *   <li>Group 1 — {@code extractViewModelId(String)}</li>
 *   <li>Group 2 — {@code extractViewModelClassName(String)}</li>
 *   <li>Group 3 — {@code findGetter(PsiClass, String)}</li>
 *   <li>Group 9 — {@code findViewModelTag(PsiElement)}</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ZulDomUtilTest {

    // ─── helpers ─────────────────────────────────────────────────────────────

    /** Creates a mock PsiMethod that appears as a public, no-arg method. */
    private PsiMethod mockPublicNoArgMethod(String name) {
        PsiMethod m = mock(PsiMethod.class);
        PsiParameterList params = mock(PsiParameterList.class);
        when(m.getName()).thenReturn(name);
        when(m.hasModifierProperty(PsiModifier.PUBLIC)).thenReturn(true);
        when(m.getParameterList()).thenReturn(params);
        when(params.getParametersCount()).thenReturn(0);
        return m;
    }

    /** Creates a mock PsiClass whose {@code getAllMethods()} returns the given methods. */
    private PsiClass mockViewModelWith(PsiMethod... methods) {
        PsiClass cls = mock(PsiClass.class);
        when(cls.getAllMethods()).thenReturn(methods);
        return cls;
    }

    /** Creates a mock XmlTag that has a {@code viewModel} attribute with the given value. */
    private XmlTag mockTagWithViewModel(String viewModelAttrValue) {
        XmlTag tag = mock(XmlTag.class);
        when(tag.getAttribute(ZulDomUtil.VIEW_MODEL)).thenReturn(mock(XmlAttribute.class));
        when(tag.getAttributeValue(ZulDomUtil.VIEW_MODEL)).thenReturn(viewModelAttrValue);
        return tag;
    }

    /** Creates a mock XmlTag that has NO {@code viewModel} attribute. */
    private XmlTag mockTagWithoutViewModel() {
        XmlTag tag = mock(XmlTag.class);
        when(tag.getAttribute(ZulDomUtil.VIEW_MODEL)).thenReturn(null);
        return tag;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GROUP 1  ZulDomUtil.extractViewModelId(String)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void extractViewModelId_vmAlias() {
        assertEquals("vm",
                ZulDomUtil.extractViewModelId("@id('vm') @init('com.example.MyViewModel')"));
    }

    @Test
    void extractViewModelId_outerAlias() {
        assertEquals("outer",
                ZulDomUtil.extractViewModelId("@id('outer') @init('com.example.OuterVM')"));
    }

    @Test
    void extractViewModelId_innerAlias() {
        assertEquals("inner",
                ZulDomUtil.extractViewModelId("@id('inner') @init('com.example.InnerVM')"));
    }

    @Test
    void extractViewModelId_missingIdToken_returnsNull() {
        assertNull(ZulDomUtil.extractViewModelId("@init('com.example.MyViewModel')"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GROUP 2  ZulDomUtil.extractViewModelClassName(String)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void extractViewModelClassName_myViewModel() {
        assertEquals("com.example.MyViewModel",
                ZulDomUtil.extractViewModelClassName(
                        "@id('vm') @init('com.example.MyViewModel')"));
    }

    @Test
    void extractViewModelClassName_nonexistentClass() {
        assertEquals("com.nonexistent.FakeVM",
                ZulDomUtil.extractViewModelClassName(
                        "@id('vm') @init('com.nonexistent.FakeVM')"));
    }

    @Test
    void extractViewModelClassName_missingInitToken_returnsNull() {
        assertNull(ZulDomUtil.extractViewModelClassName("@id('vm')"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GROUP 3  ZulDomUtil.findGetter(PsiClass, String)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void findGetter_listProperty_returnsGetList() {
        PsiClass vm = mockViewModelWith(mockPublicNoArgMethod("getList"));
        PsiMethod result = ZulDomUtil.findGetter(vm, "list");
        assertNotNull(result);
        assertEquals("getList", result.getName());
    }

    @Test
    void findGetter_nameProperty_returnsGetName() {
        PsiClass vm = mockViewModelWith(mockPublicNoArgMethod("getName"));
        PsiMethod result = ZulDomUtil.findGetter(vm, "name");
        assertNotNull(result);
        assertEquals("getName", result.getName());
    }

    @Test
    void findGetter_crewProperty_returnsGetCrew() {
        PsiClass vm = mockViewModelWith(mockPublicNoArgMethod("getCrew"));
        PsiMethod result = ZulDomUtil.findGetter(vm, "crew");
        assertNotNull(result);
        assertEquals("getCrew", result.getName());
    }

    @Test
    void findGetter_activeProperty_returnsIsActive() {
        // DSL: "Navigate to boolean getter (isXxx) from property reference"
        PsiClass vm = mockViewModelWith(mockPublicNoArgMethod("isActive"));
        PsiMethod result = ZulDomUtil.findGetter(vm, "active");
        assertNotNull(result);
        assertEquals("isActive", result.getName());
    }

    @Test
    void findGetter_basePropertyViaInheritedMethod() {
        // DSL: "Navigate to inherited getter method"
        // PsiClass.getAllMethods() returns all methods including inherited ones.
        PsiClass vm = mockViewModelWith(mockPublicNoArgMethod("getBaseProperty"));
        PsiMethod result = ZulDomUtil.findGetter(vm, "baseProperty");
        assertNotNull(result);
        assertEquals("getBaseProperty", result.getName());
    }

    @Test
    void findGetter_unknownProperty_returnsNull() {
        // DSL: "No navigation when property does not exist on ViewModel"
        PsiClass vm = mockViewModelWith(); // no methods at all
        assertNull(ZulDomUtil.findGetter(vm, "nonExistent"));
    }

    @Test
    void findGetter_ignoresFields_returnsGetterMethod() {
        // DSL: "Prefer getter method over field when both exist"
        // findGetter only calls getAllMethods(), never getFields().
        PsiClass vm = mockViewModelWith(mockPublicNoArgMethod("getName"));
        PsiMethod result = ZulDomUtil.findGetter(vm, "name");
        assertNotNull(result);
        assertEquals("getName", result.getName());
        verify(vm, never()).getFields();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GROUP 9  ZulDomUtil.findViewModelTag(PsiElement)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void findViewModelTag_returnsNearestAncestorWithViewModelAttr() {
        // PSI chain: labelTag (no viewModel) → divTag (has viewModel) → stops here.
        // findViewModelTag returns as soon as it finds the first viewModel attribute,
        // so getParent() is never called on divTag.
        XmlTag divTag   = mockTagWithViewModel("@id('inner') @init('com.example.InnerVM')");
        XmlTag labelTag = mockTagWithoutViewModel();

        when(labelTag.getParent()).thenReturn(divTag);

        XmlTag result = ZulDomUtil.findViewModelTag(labelTag);

        assertSame(divTag, result);
        assertTrue(result.getAttributeValue(ZulDomUtil.VIEW_MODEL).startsWith("@id('inner')"));
    }

    @Test
    void findViewModelTag_skipsNonViewModelAncestor_returnsOuterTag() {
        // PSI chain: labelTag (no viewModel) → windowTag (outer viewModel)
        XmlTag windowTag = mockTagWithViewModel("@id('outer') @init('com.example.OuterVM')");
        XmlTag labelTag  = mockTagWithoutViewModel();

        when(labelTag.getParent()).thenReturn(windowTag);

        XmlTag result = ZulDomUtil.findViewModelTag(labelTag);

        assertSame(windowTag, result);
        assertTrue(result.getAttributeValue(ZulDomUtil.VIEW_MODEL).startsWith("@id('outer')"));
    }

    @Test
    void findViewModelTag_noViewModelAnywhere_returnsNull() {
        // DSL: "No navigation when there is no viewModel declaration in ancestor"
        XmlTag windowTag = mockTagWithoutViewModel();
        XmlTag labelTag  = mockTagWithoutViewModel();

        when(labelTag.getParent()).thenReturn(windowTag);
        when(windowTag.getParent()).thenReturn(null);

        assertNull(ZulDomUtil.findViewModelTag(labelTag));
    }
}
