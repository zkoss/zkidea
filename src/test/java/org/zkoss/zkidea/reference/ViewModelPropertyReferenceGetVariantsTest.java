package org.zkoss.zkidea.reference;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.xml.XmlAttributeValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ViewModelPropertyReference#getVariants()}.
 *
 * <p>Mapped to the completion scenarios in vm-property-completion.feature.
 *
 * <p>Two groups of tests:
 * <ul>
 *   <li><b>Filter/null tests</b> — verify null-safety, constructor exclusion, Object-method
 *       filtering, and access-level filtering.  These never invoke {@code LookupElementBuilder}
 *       (variants array is empty) and work with plain Mockito mocks.
 *   <li><b>Variant content tests</b> — verify the lookup strings returned by each
 *       {@code LookupElement}.  These call through {@code LookupElementBuilder};
 *       icons are stored lazily so no IntelliJ application context is needed.
 * </ul>
 *
 * <p>Tests that verify rendered icons or insert-handler behaviour require an IntelliJ
 * light-test environment and are documented as scenarios in vm-property-completion.feature
 * instead of being automated here.
 *
 * <p><b>IMPORTANT</b>: mock helper methods must be called <em>before</em> any
 * {@code when()} call that uses their result as an argument — calling them inside
 * a {@code when()} array literal interferes with Mockito's stubbing recorder.
 */
@ExtendWith(MockitoExtension.class)
class ViewModelPropertyReferenceGetVariantsTest {

    private static final String CMD_ANN  = "org.zkoss.bind.annotation.Command";
    private static final String GCMD_ANN = "org.zkoss.bind.annotation.GlobalCommand";
    private static final TextRange DUMMY_RANGE = new TextRange(0, 4);

    private final XmlAttributeValue dummyElement = mock(XmlAttributeValue.class);

    // ─── factory helpers ─────────────────────────────────────────────────────
    // Must be called BEFORE any when() call that uses their result.

    /** Property-context reference (isCommandContext = false). */
    private ViewModelPropertyReference propRef(PsiClass vmClass) {
        return new ViewModelPropertyReference(dummyElement, DUMMY_RANGE, vmClass, "prop");
    }

    /** Command-context reference (isCommandContext = true). */
    private ViewModelPropertyReference cmdRef(PsiClass vmClass) {
        return new ViewModelPropertyReference(dummyElement, DUMMY_RANGE, vmClass, "prop", true);
    }

    /**
     * Mocks a public, zero-param method with a getter-style name (getXxx or isXxx).
     * {@code getReturnType()} and {@code getContainingClass()} return null — the
     * implementation handles null for both (produces empty typeText / empty tailText).
     */
    private PsiMethod publicGetter(String methodName) {
        PsiMethod m = mock(PsiMethod.class);
        lenient().when(m.getName()).thenReturn(methodName);
        lenient().when(m.hasModifierProperty(PsiModifier.PUBLIC)).thenReturn(true);
        lenient().when(m.isConstructor()).thenReturn(false);
        PsiParameterList pl = mock(PsiParameterList.class);
        lenient().when(pl.getParametersCount()).thenReturn(0);
        lenient().when(m.getParameterList()).thenReturn(pl);
        lenient().when(m.getAnnotations()).thenReturn(new PsiAnnotation[0]);
        // getReturnType() → null  (impl: returnType != null ? ... : "")
        // getContainingClass() → null  (impl: ... != null ? ... : "")
        return m;
    }

    /**
     * Mocks a public method that is NOT a getter (name does not start with get/is).
     *
     * @param paramCount used in Pass 2 paramHint and insert-handler logic
     */
    private PsiMethod publicMethod(String methodName, int paramCount) {
        PsiMethod m = mock(PsiMethod.class);
        lenient().when(m.getName()).thenReturn(methodName);
        lenient().when(m.hasModifierProperty(PsiModifier.PUBLIC)).thenReturn(true);
        lenient().when(m.isConstructor()).thenReturn(false);
        PsiParameterList pl = mock(PsiParameterList.class);
        lenient().when(pl.getParametersCount()).thenReturn(paramCount);
        lenient().when(m.getParameterList()).thenReturn(pl);
        lenient().when(m.getAnnotations()).thenReturn(new PsiAnnotation[0]);
        return m;
    }

    /**
     * Mocks a @Command-annotated public zero-param method.
     *
     * @param annotationValue {@code "\"save\""} for {@code @Command(value="save")},
     *                        or {@code null} for a bare {@code @Command}
     */
    private PsiMethod commandMethod(String methodName, String annotationValue) {
        PsiMethod m = publicMethod(methodName, 0);
        PsiAnnotation ann = mock(PsiAnnotation.class);
        when(ann.getQualifiedName()).thenReturn(CMD_ANN);
        when(m.getAnnotations()).thenReturn(new PsiAnnotation[]{ann});
        if (annotationValue != null) {
            PsiAnnotationMemberValue val = mock(PsiAnnotationMemberValue.class);
            when(val.getText()).thenReturn(annotationValue);
            when(ann.findDeclaredAttributeValue("value")).thenReturn(val);
        } else {
            lenient().when(ann.findDeclaredAttributeValue("value")).thenReturn(null);
        }
        return m;
    }

    /** Mocks a @GlobalCommand-annotated public zero-param method. */
    private PsiMethod globalCommandMethod(String methodName) {
        PsiMethod m = publicMethod(methodName, 0);
        PsiAnnotation ann = mock(PsiAnnotation.class);
        when(ann.getQualifiedName()).thenReturn(GCMD_ANN);
        when(m.getAnnotations()).thenReturn(new PsiAnnotation[]{ann});
        lenient().when(ann.findDeclaredAttributeValue("value")).thenReturn(null);
        return m;
    }

    /** Extracts the lookup string from every element in the variants array. */
    private List<String> lookupStrings(Object[] variants) {
        return Arrays.stream(variants)
                .map(v -> ((LookupElement) v).getLookupString())
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Null-safety — property context
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void getVariants_nullOwnerClass_propertyContext_returnsEmptyArray() {
        Object[] variants = propRef(null).getVariants();
        assertNotNull(variants);
        assertEquals(0, variants.length);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Null-safety — command context
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void getVariants_nullOwnerClass_commandContext_returnsEmptyArray() {
        Object[] variants = cmdRef(null).getVariants();
        assertNotNull(variants);
        assertEquals(0, variants.length);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Method filtering — these tests produce empty variants arrays so they
    // never invoke LookupElementBuilder or AllIcons
    // ═══════════════════════════════════════════════════════════════════════

    // Feature: Empty class produces no variants
    @Test
    void getVariants_classWithNoMethods_returnsEmptyArray() {
        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[0]);

        assertEquals(0, propRef(vmClass).getVariants().length);
    }

    // Feature: Common Object utility methods are filtered out
    @Test
    void getVariants_allObjectMethods_propertyContext_returnsEmptyArray() {
        // Pass 1: none match getXxx/isXxx → skipped
        // Pass 2: all filtered by isObjectMethod()
        PsiMethod toString  = publicMethod("toString",   0);
        PsiMethod hashCode  = publicMethod("hashCode",   0);
        PsiMethod notify    = publicMethod("notify",     0);
        PsiMethod notifyAll = publicMethod("notifyAll",  0);
        PsiMethod wait0     = publicMethod("wait",       0);
        PsiMethod finalize0 = publicMethod("finalize",   0);

        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods())
                .thenReturn(new PsiMethod[]{toString, hashCode, notify, notifyAll, wait0, finalize0});

        assertEquals(0, propRef(vmClass).getVariants().length);
    }

    // Feature: equals has 1 param → skipped in Pass 1; isObjectMethod → skipped in Pass 2
    @Test
    void getVariants_equalsMethod_filteredByParameterCountAndObjectMethodCheck() {
        PsiMethod equals = publicMethod("equals", 1);

        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{equals});

        assertEquals(0, propRef(vmClass).getVariants().length);
    }

    // Feature: Constructor is not suggested
    @Test
    void getVariants_constructorMethod_notIncludedInPropertyVariants() {
        PsiMethod ctor = mock(PsiMethod.class);
        lenient().when(ctor.getName()).thenReturn("MyViewModel");
        lenient().when(ctor.hasModifierProperty(PsiModifier.PUBLIC)).thenReturn(true);
        lenient().when(ctor.isConstructor()).thenReturn(true);
        PsiParameterList pl = mock(PsiParameterList.class);
        lenient().when(pl.getParametersCount()).thenReturn(0);
        lenient().when(ctor.getParameterList()).thenReturn(pl);
        lenient().when(ctor.getAnnotations()).thenReturn(new PsiAnnotation[0]);

        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{ctor});

        assertEquals(0, propRef(vmClass).getVariants().length);
    }

    // Feature: Non-public method is excluded
    @Test
    void getVariants_nonPublicMethod_notIncludedInPropertyVariants() {
        PsiMethod prot = mock(PsiMethod.class);
        lenient().when(prot.hasModifierProperty(PsiModifier.PUBLIC)).thenReturn(false);
        lenient().when(prot.getAnnotations()).thenReturn(new PsiAnnotation[0]);

        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{prot});

        assertEquals(0, propRef(vmClass).getVariants().length);
    }

    // Feature: Getter with parameters is excluded from property suggestions
    @Test
    void getVariants_getterWithParameter_notIncluded() {
        // Pass 1: parametersCount != 0 → continue.  Pass 2: getPropertyName("getFiltered") != null → skip
        PsiMethod paramGetter = mock(PsiMethod.class);
        lenient().when(paramGetter.getName()).thenReturn("getFiltered");
        lenient().when(paramGetter.hasModifierProperty(PsiModifier.PUBLIC)).thenReturn(true);
        lenient().when(paramGetter.isConstructor()).thenReturn(false);
        PsiParameterList pl = mock(PsiParameterList.class);
        lenient().when(pl.getParametersCount()).thenReturn(1);
        lenient().when(paramGetter.getParameterList()).thenReturn(pl);
        lenient().when(paramGetter.getAnnotations()).thenReturn(new PsiAnnotation[0]);

        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{paramGetter});

        assertEquals(0, propRef(vmClass).getVariants().length);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Property context — getter-backed variants (Pass 1)
    // ═══════════════════════════════════════════════════════════════════════

    // Feature: getXxx getter appears as property name without "get" prefix
    @Test
    void getVariants_getterMethod_lookupStringIsPropertyName() {
        PsiMethod getName = publicGetter("getName");

        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{getName});

        Object[] variants = propRef(vmClass).getVariants();
        assertEquals(1, variants.length);
        assertEquals("name", ((LookupElement) variants[0]).getLookupString());
    }

    // Feature: Boolean getter (isXxx) stripped to property name
    @Test
    void getVariants_booleanGetterIsXxx_lookupStringIsPropertyName() {
        PsiMethod isActive = publicGetter("isActive");

        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{isActive});

        Object[] variants = propRef(vmClass).getVariants();
        assertEquals(1, variants.length);
        assertEquals("active", ((LookupElement) variants[0]).getLookupString());
    }

    @Test
    void getVariants_multipleGetters_allPropertyNamesPresent() {
        PsiMethod getName  = publicGetter("getName");
        PsiMethod getList  = publicGetter("getList");
        PsiMethod isActive = publicGetter("isActive");

        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{getName, getList, isActive});

        List<String> keys = lookupStrings(propRef(vmClass).getVariants());
        assertTrue(keys.contains("name"));
        assertTrue(keys.contains("list"));
        assertTrue(keys.contains("active"));
    }

    // Feature: Duplicate property name is deduplicated
    @Test
    void getVariants_sameGetterNameTwice_propertyAppearsOnce() {
        // Two distinct mocks that both produce property "name"
        PsiMethod getName1 = publicGetter("getName");
        PsiMethod getName2 = publicGetter("getName");

        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{getName1, getName2});

        long count = lookupStrings(propRef(vmClass).getVariants()).stream()
                .filter("name"::equals)
                .count();
        assertEquals(1, count, "'name' must appear exactly once");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Property context — non-getter method variants (Pass 2)
    // ═══════════════════════════════════════════════════════════════════════

    // Feature: Non-getter public method is suggested
    @Test
    void getVariants_nonGetterPublicZeroParamMethod_appearsWithMethodName() {
        PsiMethod saveItem = publicMethod("saveItem", 0);

        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{saveItem});

        assertTrue(lookupStrings(propRef(vmClass).getVariants()).contains("saveItem"));
    }

    @Test
    void getVariants_nonGetterMethodWithParams_appearsWithMethodName() {
        PsiMethod setValue = publicMethod("setValue", 1);

        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{setValue});

        assertTrue(lookupStrings(propRef(vmClass).getVariants()).contains("setValue"));
    }

    // Feature: Getter and non-getter mixed — both present
    @Test
    void getVariants_getterAndNonGetterMixed_bothPresent() {
        PsiMethod getName  = publicGetter("getName");      // Pass 1 → "name"
        PsiMethod saveItem = publicMethod("saveItem", 0);  // Pass 2 → "saveItem"

        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{getName, saveItem});

        List<String> keys = lookupStrings(propRef(vmClass).getVariants());
        assertTrue(keys.contains("name"));
        assertTrue(keys.contains("saveItem"));
    }

    // Feature: Object method filtered even when mixed with real methods
    @Test
    void getVariants_objectMethodMixedWithRealMethod_objectMethodFiltered() {
        PsiMethod toString  = publicMethod("toString",  0); // isObjectMethod → filtered
        PsiMethod saveItem  = publicMethod("saveItem",  0); // real method → included

        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{toString, saveItem});

        List<String> keys = lookupStrings(propRef(vmClass).getVariants());
        assertFalse(keys.contains("toString"), "'toString' must be filtered by isObjectMethod()");
        assertTrue(keys.contains("saveItem"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Command context
    // ═══════════════════════════════════════════════════════════════════════

    // Feature: Empty class in command context → empty
    @Test
    void getVariants_commandContext_emptyClass_returnsEmptyArray() {
        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[0]);

        assertEquals(0, cmdRef(vmClass).getVariants().length);
    }

    // Feature: @Command method name appears in command context
    @Test
    void getVariants_commandContext_commandAnnotatedMethod_appearsWithMethodName() {
        PsiMethod saveItem = commandMethod("saveItem", null);

        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{saveItem});

        assertTrue(lookupStrings(cmdRef(vmClass).getVariants()).contains("saveItem"));
    }

    // Feature: @Command with explicit annotation value shows value, not method name
    @Test
    void getVariants_commandContext_annotationValueUsedAsLookupString() {
        // persistItem annotated @Command(value="save") → completion shows "save"
        PsiMethod persistItem = commandMethod("persistItem", "\"save\"");

        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{persistItem});

        List<String> keys = lookupStrings(cmdRef(vmClass).getVariants());
        assertTrue(keys.contains("save"),          "annotation value 'save' must be used");
        assertFalse(keys.contains("persistItem"),  "method name 'persistItem' must not appear");
    }

    // Feature: @GlobalCommand method appears in command context
    @Test
    void getVariants_commandContext_globalCommandMethod_appearsInVariants() {
        PsiMethod broadcast = globalCommandMethod("broadcast");

        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{broadcast});

        assertTrue(lookupStrings(cmdRef(vmClass).getVariants()).contains("broadcast"));
    }

    // Feature: Plain method without @Command is not suggested in command context
    @Test
    void getVariants_commandContext_plainMethodWithoutAnnotation_notSuggested() {
        PsiMethod helper = publicMethod("helperMethod", 0);

        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{helper});

        assertEquals(0, cmdRef(vmClass).getVariants().length,
                "Non-@Command methods must not appear in command context");
    }

    // Feature: Multiple commands — all appear
    @Test
    void getVariants_commandContext_multipleCommands_allPresent() {
        PsiMethod saveItem  = commandMethod("saveItem",  null);
        PsiMethod validate  = commandMethod("validate",  null);
        PsiMethod broadcast = globalCommandMethod("broadcast");

        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{saveItem, validate, broadcast});

        List<String> keys = lookupStrings(cmdRef(vmClass).getVariants());
        assertTrue(keys.contains("saveItem"));
        assertTrue(keys.contains("validate"));
        assertTrue(keys.contains("broadcast"));
    }
}
