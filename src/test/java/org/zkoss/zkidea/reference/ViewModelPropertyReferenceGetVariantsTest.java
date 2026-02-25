package org.zkoss.zkidea.reference;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.xml.XmlAttributeValue;
import org.junit.jupiter.api.BeforeEach;
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
 * <p>The ViewModel under test is {@link MyViewModel}, whose methods are reflected
 * into a mock {@link PsiClass} by {@link PsiClassMocker}.  This keeps {@code MyViewModel.java}
 * as the single source of truth for what the ViewModel contains, avoiding the need
 * to hand-wire individual {@link PsiMethod} mocks in every test.
 *
 * <p>The only tests that still construct targeted {@link PsiMethod} mocks are the
 * Object-method filtering tests, because {@code notify()}, {@code notifyAll()}, and
 * {@code wait()} are {@code final} in {@link Object} and therefore cannot be declared
 * in any real Java subclass.
 *
 * <p>Tests that verify rendered icons or insert-handler behaviour require an IntelliJ
 * light-test environment and are documented as scenarios in vm-property-completion.feature
 * instead of being automated here.
 */
@ExtendWith(MockitoExtension.class)
class ViewModelPropertyReferenceGetVariantsTest {

    private static final TextRange DUMMY_RANGE = new TextRange(0, 4);

    private final XmlAttributeValue dummyElement = mock(XmlAttributeValue.class);

    /** Reflects {@link MyViewModel} into a fresh mock per test. */
    private PsiClass vmClass;

    @BeforeEach
    void buildVmClass() {
        vmClass = PsiClassMocker.from(MyViewModel.class);
    }

    // ─── factory helpers ──────────────────────────────────────────────────────

    /** Property-context reference backed by {@link MyViewModel}. */
    private ViewModelPropertyReference propRef() {
        return new ViewModelPropertyReference(dummyElement, DUMMY_RANGE, vmClass, "prop");
    }

    /** Property-context reference backed by the given class. */
    private ViewModelPropertyReference propRef(PsiClass cls) {
        return new ViewModelPropertyReference(dummyElement, DUMMY_RANGE, cls, "prop");
    }

    /** Command-context reference backed by {@link MyViewModel}. */
    private ViewModelPropertyReference cmdRef() {
        return new ViewModelPropertyReference(dummyElement, DUMMY_RANGE, vmClass, "prop", true);
    }

    /** Command-context reference backed by the given class. */
    private ViewModelPropertyReference cmdRef(PsiClass cls) {
        return new ViewModelPropertyReference(dummyElement, DUMMY_RANGE, cls, "prop", true);
    }

    private List<String> lookupStrings(Object[] variants) {
        return Arrays.stream(variants)
                .map(v -> ((LookupElement) v).getLookupString())
                .collect(Collectors.toList());
    }

    // ─── helpers for Object-method filtering tests ────────────────────────────
    // notify(), notifyAll(), wait() are final in Object and cannot be declared in
    // a Java subclass, so these three tests require targeted PsiMethod mocks.

    private static PsiMethod publicMethod(String name, int paramCount) {
        PsiMethod m = mock(PsiMethod.class);
        lenient().when(m.getName()).thenReturn(name);
        lenient().when(m.hasModifierProperty(PsiModifier.PUBLIC)).thenReturn(true);
        lenient().when(m.isConstructor()).thenReturn(false);
        PsiParameterList pl = mock(PsiParameterList.class);
        lenient().when(pl.getParametersCount()).thenReturn(paramCount);
        lenient().when(m.getParameterList()).thenReturn(pl);
        lenient().when(m.getAnnotations()).thenReturn(new com.intellij.psi.PsiAnnotation[0]);
        return m;
    }

    private static PsiClass mockClassWith(PsiMethod... methods) {
        PsiClass cls = mock(PsiClass.class);
        lenient().when(cls.getAllMethods()).thenReturn(methods);
        return cls;
    }

    // ─── inner fixture classes ────────────────────────────────────────────────

    /** Used by the "empty class → no variants" tests. */
    private static class EmptyViewModel {}

    /**
     * Two-level hierarchy where {@code getName()} is declared in both levels.
     * {@link PsiClassMocker#from} traverses the hierarchy and includes both,
     * exercising the deduplication logic in {@code getPropertyVariants()}.
     */
    private static class NameBase    { public String getName() { return null; } }
    private static class NameDerived extends NameBase { @Override public String getName() { return null; } }

    // ═══════════════════════════════════════════════════════════════════════════
    // Method filtering
    // ═══════════════════════════════════════════════════════════════════════════

    // Feature: Empty class produces no variants
    @Test
    void getVariants_classWithNoMethods_returnsEmptyArray() {
        assertEquals(0, propRef(PsiClassMocker.from(EmptyViewModel.class)).getVariants().length);
    }

    // Feature: Common Object utility methods are filtered out
    // (notify/notifyAll/wait are final — cannot be represented in a real subclass)
    @Test
    void getVariants_allObjectMethods_propertyContext_returnsEmptyArray() {
        PsiMethod toString  = publicMethod("toString",   0);
        PsiMethod hashCode  = publicMethod("hashCode",   0);
        PsiMethod notify    = publicMethod("notify",     0);
        PsiMethod notifyAll = publicMethod("notifyAll",  0);
        PsiMethod wait0     = publicMethod("wait",       0);
        PsiMethod finalize0 = publicMethod("finalize",   0);
        assertEquals(0,
                propRef(mockClassWith(toString, hashCode, notify, notifyAll, wait0, finalize0))
                        .getVariants().length);
    }

    // Feature: equals has 1 param → skipped in Pass 1; isObjectMethod → skipped in Pass 2
    @Test
    void getVariants_equalsMethod_filteredByObjectMethodCheck() {
        assertEquals(0, propRef(mockClassWith(publicMethod("equals", 1))).getVariants().length);
    }

    // Feature: Object method mixed with a real method — Object method is filtered
    @Test
    void getVariants_objectMethodMixedWithRealMethod_objectMethodFiltered() {
        PsiMethod toString = publicMethod("toString", 0);
        PsiMethod saveItem = publicMethod("saveItem", 0);
        List<String> keys = lookupStrings(propRef(mockClassWith(toString, saveItem)).getVariants());
        assertFalse(keys.contains("toString"), "'toString' must be filtered by isObjectMethod()");
        assertTrue(keys.contains("saveItem"));
    }

    // Feature: Constructor is not suggested
    @Test
    void getVariants_constructorMethod_notIncludedInPropertyVariants() {
        // PsiClassMocker includes MyViewModel's default constructor (isConstructor = true)
        assertFalse(lookupStrings(propRef().getVariants()).contains("MyViewModel"));
    }

    // Feature: Non-public method is excluded
    @Test
    void getVariants_nonPublicMethod_notIncludedInPropertyVariants() {
        // MyViewModel.protectedHelper() is protected → must not appear
        assertFalse(lookupStrings(propRef().getVariants()).contains("protectedHelper"));
    }

    // Feature: Getter with parameters is excluded from property suggestions
    @Test
    void getVariants_getterWithParameter_notIncluded() {
        // MyViewModel.getFiltered(String query) → property "filtered" must not appear
        assertFalse(lookupStrings(propRef().getVariants()).contains("filtered"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Property context — getter-backed variants (Pass 1)
    // ═══════════════════════════════════════════════════════════════════════════

    // Feature: getXxx getter appears as property name without "get" prefix
    @Test
    void getVariants_getterMethod_lookupStringIsPropertyName() {
        // MyViewModel.getName() → "name"
        assertTrue(lookupStrings(propRef().getVariants()).contains("name"));
    }

    // Feature: Boolean getter (isXxx) stripped to property name
    @Test
    void getVariants_booleanGetterIsXxx_lookupStringIsPropertyName() {
        // MyViewModel.isActive() → "active" (not "isActive")
        List<String> keys = lookupStrings(propRef().getVariants());
        assertTrue(keys.contains("active"));
        assertFalse(keys.contains("isActive"));
    }

    @Test
    void getVariants_multipleGetters_allPropertyNamesPresent() {
        List<String> keys = lookupStrings(propRef().getVariants());
        assertTrue(keys.contains("name"));
        assertTrue(keys.contains("list"));
        assertTrue(keys.contains("active"));
    }

    // Feature: Duplicate property name is deduplicated
    @Test
    void getVariants_sameGetterNameTwice_propertyAppearsOnce() {
        // NameDerived and NameBase both declare getName(); PsiClassMocker includes both.
        long count = lookupStrings(propRef(PsiClassMocker.from(NameDerived.class)).getVariants())
                .stream().filter("name"::equals).count();
        assertEquals(1, count, "'name' must appear exactly once");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Property context — non-getter method variants (Pass 2)
    // ═══════════════════════════════════════════════════════════════════════════

    // Feature: Non-getter public zero-param method is suggested
    @Test
    void getVariants_nonGetterPublicZeroParamMethod_appearsWithMethodName() {
        // MyViewModel.saveItem() → "saveItem"
        assertTrue(lookupStrings(propRef().getVariants()).contains("saveItem"));
    }

    @Test
    void getVariants_nonGetterMethodWithParams_appearsWithMethodName() {
        // MyViewModel.setValue(String) → "setValue"
        assertTrue(lookupStrings(propRef().getVariants()).contains("setValue"));
    }

    // Feature: Getter and non-getter mixed — both present
    @Test
    void getVariants_getterAndNonGetterMixed_bothPresent() {
        List<String> keys = lookupStrings(propRef().getVariants());
        assertTrue(keys.contains("name"));
        assertTrue(keys.contains("saveItem"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Command context
    // ═══════════════════════════════════════════════════════════════════════════

    // Feature: Empty class in command context → empty
    @Test
    void getVariants_commandContext_emptyClass_returnsEmptyArray() {
        assertEquals(0, cmdRef(PsiClassMocker.from(EmptyViewModel.class)).getVariants().length);
    }

    // Feature: @Command method name appears in command context
    @Test
    void getVariants_commandContext_commandAnnotatedMethod_appearsWithMethodName() {
        // MyViewModel.@Command saveItem() → "saveItem"
        assertTrue(lookupStrings(cmdRef().getVariants()).contains("saveItem"));
    }

    // Feature: @Command with explicit annotation value shows value, not method name
    @Test
    void getVariants_commandContext_annotationValueUsedAsLookupString() {
        // MyViewModel.persistItem() has @Command("save") → completion shows "save"
        List<String> keys = lookupStrings(cmdRef().getVariants());
        assertTrue(keys.contains("save"),         "annotation value 'save' must be used");
        assertFalse(keys.contains("persistItem"), "method name 'persistItem' must not appear");
    }

    // Feature: @GlobalCommand method appears in command context
    @Test
    void getVariants_commandContext_globalCommandMethod_appearsInVariants() {
        // MyViewModel.@GlobalCommand broadcast() → "broadcast"
        assertTrue(lookupStrings(cmdRef().getVariants()).contains("broadcast"));
    }

    // Feature: Plain method without @Command is not suggested in command context
    @Test
    void getVariants_commandContext_plainMethodWithoutAnnotation_notSuggested() {
        // MyViewModel.init() has no @Command annotation → must not appear
        assertFalse(lookupStrings(cmdRef().getVariants()).contains("init"));
    }

    // Feature: Multiple commands — all appear
    @Test
    void getVariants_commandContext_multipleCommands_allPresent() {
        List<String> keys = lookupStrings(cmdRef().getVariants());
        assertTrue(keys.contains("saveItem"));
        assertTrue(keys.contains("validate"));
        assertTrue(keys.contains("broadcast"));
    }
}
