package org.zkoss.zkidea.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlAttributeValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zkoss.zkidea.dom.ZulDomUtil;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ISA-level unit tests for {@link ViewModelPropertyReference#resolve()}.
 * Covers Group 7 from mvvm_property_navigation.isa.feature.
 *
 * <p>Strategy: {@code resolve()} delegates entirely to
 * {@code ZulDomUtil.findGetterOrMethod(ownerClass, propertyName)}.
 * We use {@code mockStatic(ZulDomUtil.class)} to control the return value
 * without needing a live IntelliJ project. The {@code XmlAttributeValue}
 * element argument is a plain mock — its content is irrelevant for resolve().
 */
@ExtendWith(MockitoExtension.class)
class ViewModelPropertyReferenceTest {

    /** Dummy TextRange used to construct references — value is irrelevant for resolve(). */
    private static final TextRange DUMMY_RANGE = new TextRange(0, 4);

    private final XmlAttributeValue dummyElement = mock(XmlAttributeValue.class);

    // ─── helper ──────────────────────────────────────────────────────────────

    /**
     * Builds a reference, stubs {@code ZulDomUtil.findGetterOrMethod} via the
     * provided static mock, calls {@code resolve()}, and returns the result.
     */
    private PsiElement resolveWith(MockedStatic<ZulDomUtil> util,
                                   PsiClass ownerClass,
                                   String propertyName,
                                   PsiMethod stubReturn) {
        util.when(() -> ZulDomUtil.findGetterOrMethod(ownerClass, propertyName))
                .thenReturn(stubReturn);
        ViewModelPropertyReference ref =
                new ViewModelPropertyReference(dummyElement, DUMMY_RANGE, ownerClass, propertyName);
        return ref.resolve();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GROUP 7  ViewModelPropertyReference.resolve()
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void resolve_listProperty_returnsGetList() {
        PsiClass mockClass = mock(PsiClass.class);
        PsiMethod getList  = mock(PsiMethod.class);
        when(getList.getName()).thenReturn("getList");

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            PsiElement result = resolveWith(util, mockClass, "list", getList);
            assertSame(getList, result);
            assertEquals("getList", ((PsiMethod) result).getName());
        }
    }

    @Test
    void resolve_nameProperty_returnsGetName() {
        PsiClass mockClass = mock(PsiClass.class);
        PsiMethod getName  = mock(PsiMethod.class);
        when(getName.getName()).thenReturn("getName");

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            PsiElement result = resolveWith(util, mockClass, "name", getName);
            assertSame(getName, result);
        }
    }

    @Test
    void resolve_activeProperty_returnsIsActive() {
        // DSL: "Navigate to boolean getter (isXxx) from property reference"
        PsiClass mockClass = mock(PsiClass.class);
        PsiMethod isActive = mock(PsiMethod.class);
        when(isActive.getName()).thenReturn("isActive");

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            PsiElement result = resolveWith(util, mockClass, "active", isActive);
            assertSame(isActive, result);
        }
    }

    @Test
    void resolve_crewProperty_returnsGetCrew() {
        PsiClass mockClass = mock(PsiClass.class);
        PsiMethod getCrew  = mock(PsiMethod.class);
        when(getCrew.getName()).thenReturn("getCrew");

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            PsiElement result = resolveWith(util, mockClass, "crew", getCrew);
            assertSame(getCrew, result);
        }
    }

    @Test
    void resolve_baseProperty_returnsGetBaseProperty() {
        // DSL: "Navigate to inherited getter method"
        PsiClass mockClass        = mock(PsiClass.class);
        PsiMethod getBaseProperty = mock(PsiMethod.class);
        when(getBaseProperty.getName()).thenReturn("getBaseProperty");

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            PsiElement result = resolveWith(util, mockClass, "baseProperty", getBaseProperty);
            assertSame(getBaseProperty, result);
        }
    }

    @Test
    void resolve_selectedItemProperty_returnsGetSelectedItem() {
        // DSL: "Navigate to property in @command expression parameter"
        PsiClass mockClass          = mock(PsiClass.class);
        PsiMethod getSelectedItem   = mock(PsiMethod.class);
        when(getSelectedItem.getName()).thenReturn("getSelectedItem");

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            PsiElement result = resolveWith(util, mockClass, "selectedItem", getSelectedItem);
            assertSame(getSelectedItem, result);
        }
    }

    @Test
    void resolve_unknownProperty_returnsNull() {
        // DSL: "No navigation when property does not exist on ViewModel"
        PsiClass mockClass = mock(PsiClass.class);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            // stub findGetterOrMethod to return null (property not found)
            PsiElement result = resolveWith(util, mockClass, "nonExistent", null);
            assertNull(result);
        }
    }

    @Test
    void resolve_nullOwnerClass_returnsNullWithoutCallingFindGetterOrMethod() {
        // resolve() must guard against null ownerClass and return null immediately.
        // No MockedStatic needed — the null guard fires before any ZulDomUtil call.
        ViewModelPropertyReference ref =
                new ViewModelPropertyReference(dummyElement, DUMMY_RANGE, null, "list");
        assertNull(ref.resolve());
    }
}
