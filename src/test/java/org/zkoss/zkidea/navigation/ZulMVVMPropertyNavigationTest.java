package org.zkoss.zkidea.navigation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.jupiter.api.*;
import org.zkoss.zkidea.actions.ZulMVVMPropertyNavigationHandler;

/**
 * Test suite for MVVM Binding Expression Property Navigation.
 * Follows the scenarios defined in src/test/resources/features/mvvm_property_navigation.feature
 */
public class ZulMVVMPropertyNavigationTest extends BasePlatformTestCase {

    @BeforeEach
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @AfterEach
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @DisplayName("Scenario: Navigate to getter method from simple property reference")
    public void testNavigateToGetterSimple() {
        setupViewModelClasses();
        myFixture.configureByText("test.zul", """
                <window viewModel="@id('vm') @init('com.example.MyViewModel')">
                    <grid model="@load(vm.li<caret>st)"/>
                </window>""");
        verifyNavigation("getList");
    }

    @Test
    @DisplayName("Scenario: Navigate to getter method for String property")
    public void testNavigateToNameGetter() {
        setupViewModelClasses();
        myFixture.configureByText("test.zul", """
                <window viewModel="@id('vm') @init('com.example.MyViewModel')">
                    <label value="@load(vm.na<caret>me)"/>
                </window>""");
        verifyNavigation("getName");
    }

    @Test
    @DisplayName("Scenario: Navigate to boolean getter (isXxx) from property reference")
    public void testNavigateToBooleanGetter() {
        setupViewModelClasses();
        myFixture.configureByText("test.zul", """
                <window viewModel="@id('vm') @init('com.example.MyViewModel')">
                    <label value="@load(vm.act<caret>ive)"/>
                </window>""");
        verifyNavigation("isActive");
    }

    @Test
    @DisplayName("Scenario: Navigate to getter on nested property path (first segment)")
    public void testNavigateToNestedFirstSegment() {
        setupViewModelClasses();
        myFixture.configureByText("test.zul", """
                <window viewModel="@id('vm') @init('com.example.MyViewModel')">
                    <label value="@load(vm.cre<caret>w.name)"/>
                </window>""");
        verifyNavigation("getCrew");
    }

    @Test
    @DisplayName("Scenario: Navigate to getter on nested property path (second segment)")
    public void testNavigateToNestedSecondSegment() {
        setupViewModelClasses();
        myFixture.configureByText("test.zul", """
                <window viewModel="@id('vm') @init('com.example.MyViewModel')">
                    <label value="@load(vm.crew.na<caret>me)"/>
                </window>""");
        verifyNavigation("getName", "com.example.CrewModel");
    }

    @Test
    @DisplayName("Scenario Outline: Navigate from various MVVM binding annotations")
    public void testNavigateFromDifferentAnnotations() {
        setupViewModelClasses();
        String[] annotations = {"@load", "@init", "@bind", "@save"};
        
        for (String annotation : annotations) {
            myFixture.configureByText("test.zul", String.format("""
                    <window viewModel="@id('vm') @init('com.example.MyViewModel')">
                        <grid model="%s(vm.li<caret>st)"/>
                    </window>""", annotation));
            verifyNavigation("getList");
        }
    }

    @Test
    @DisplayName("Scenario: No navigation when property does not exist on ViewModel")
    public void testNoNavigationForNonExistentProperty() {
        setupViewModelClasses();
        myFixture.configureByText("test.zul", """
                <window viewModel="@id('vm') @init('com.example.MyViewModel')">
                    <grid model="@load(vm.nonExis<caret>tent)"/>
                </window>""");
        
        PsiElement target = getNavigationTarget();
        Assertions.assertNull(target, "Should not find any declaration for non-existent property");
    }

    @Test
    @DisplayName("Scenario: No navigation when there is no viewModel declaration in ancestor")
    public void testNoNavigationWithoutViewModel() {
        setupViewModelClasses();
        myFixture.configureByText("test.zul", """
                <window>
                    <label value="@load(vm.li<caret>st)"/>
                </window>""");
        
        PsiElement target = getNavigationTarget();
        Assertions.assertNull(target, "Should not navigate when no viewModel is declared");
    }

    @Test
    @DisplayName("Scenario: Resolve ViewModel with different @id name")
    public void testDifferentVmId() {
        setupViewModelClasses();
        myFixture.configureByText("test.zul", """
                <window viewModel="@id('myVm') @init('com.example.MyViewModel')">
                    <grid model="@load(myVm.li<caret>st)"/>
                </window>""");
        verifyNavigation("getList");
    }

    @Test
    @DisplayName("Scenario: Navigate to inherited getter method")
    public void testInheritedProperty() {
        myFixture.addFileToProject("com/example/BaseViewModel.java", """
                package com.example;
                public class BaseViewModel {
                    public String getBaseProperty() { return null; }
                }""");
        myFixture.addFileToProject("com/example/MyViewModel.java", """
                package com.example;
                public class MyViewModel extends BaseViewModel {
                }""");

        myFixture.configureByText("test.zul", """
                <window viewModel="@id('vm') @init('com.example.MyViewModel')">
                    <label value="@load(vm.basePro<caret>perty)"/>
                </window>""");
        verifyNavigation("getBaseProperty", "com.example.BaseViewModel");
    }

    @Test
    @DisplayName("Scenario: Resolve correct ViewModel in nested viewModel declarations (inner)")
    public void testNestedViewModelsInner() {
        myFixture.addFileToProject("com/example/OuterVM.java", """
                package com.example;
                public class OuterVM {
                    public String getOuterName() { return null; }
                }""");
        myFixture.addFileToProject("com/example/InnerVM.java", """
                package com.example;
                public class InnerVM {
                    public String getInnerName() { return null; }
                }""");

        myFixture.configureByText("test.zul", """
                <window viewModel="@id('outer') @init('com.example.OuterVM')">
                    <div viewModel="@id('inner') @init('com.example.InnerVM')">
                        <label value="@load(inner.innerNa<caret>me)"/>
                    </div>
                </window>""");

        verifyNavigation("getInnerName", "com.example.InnerVM");
    }

    @Test
    @DisplayName("Scenario: Resolve correct ViewModel in nested viewModel declarations (outer)")
    public void testNestedViewModelsOuter() {
        myFixture.addFileToProject("com/example/OuterVM.java", """
                package com.example;
                public class OuterVM {
                    public String getOuterName() { return null; }
                }""");
        myFixture.addFileToProject("com/example/InnerVM.java", """
                package com.example;
                public class InnerVM {
                    public String getInnerName() { return null; }
                }""");

        myFixture.configureByText("test.zul", """
                <window viewModel="@id('outer') @init('com.example.OuterVM')">
                    <div viewModel="@id('inner') @init('com.example.InnerVM')">
                        <label value="@load(inner.innerName)"/>
                    </div>
                    <label value="@load(outer.outerNa<caret>me)"/>
                </window>""");

        verifyNavigation("getOuterName", "com.example.OuterVM");
    }

    @Test
    @DisplayName("Scenario: Navigate to property in @command expression parameter")
    public void testCommandParameterProperty() {
        setupViewModelClasses();
        myFixture.configureByText("test.zul", """
                <window viewModel="@id('vm') @init('com.example.MyViewModel')">
                    <button onClick="@command('delete', item=vm.li<caret>st)"/>
                </window>""");
        // Our current handler only handles @load, @init, etc. Let's see if we can support @command.
        verifyNavigation("getList");
    }

    @Test
    @DisplayName("Scenario: No navigation when ViewModel class cannot be resolved")
    public void testNoNavigationForUnresolvedViewModelClass() {
        myFixture.configureByText("test.zul", """
                <window viewModel="@id('vm') @init('com.nonexistent.FakeVM')">
                    <grid model="@load(vm.li<caret>st)"/>
                </window>""");
        
        PsiElement target = getNavigationTarget();
        Assertions.assertNull(target, "Should not navigate when ViewModel class is missing");
    }

    @Test
    @DisplayName("Scenario: No navigation outside of binding expression")
    public void testNoNavigationOutsideBinding() {
        setupViewModelClasses();
        myFixture.configureByText("test.zul", """
                <window viewModel="@id('vm') @init('com.example.MyViewModel')">
                    <label value="plain text with vm.li<caret>st"/>
                </window>""");
        
        PsiElement target = getNavigationTarget();
        Assertions.assertNull(target, "Should not navigate outside of @load(...) etc.");
    }

    @Test
    @DisplayName("Scenario: Prefer getter method over field when both exist")
    public void testPreferGetterOverField() {
        myFixture.addFileToProject("com/example/MyViewModel.java", """
                package com.example;
                public class MyViewModel {
                    private String name;
                    public String getName() { return name; }
                }""");

        myFixture.configureByText("test.zul", """
                <window viewModel="@id('vm') @init('com.example.MyViewModel')">
                    <label value="@load(vm.na<caret>me)"/>
                </window>""");
        
        PsiElement target = getNavigationTarget();
        Assertions.assertNotNull(target);
        Assertions.assertTrue(target instanceof PsiMethod);
        Assertions.assertEquals("getName", ((PsiMethod)target).getName());
    }

    @Test
    @DisplayName("Scenario: Navigate to ViewModel class from ViewModel ID prefix")
    public void testNavigateToVmClass() {
        setupViewModelClasses();
        myFixture.configureByText("test.zul", """
                <window viewModel="@id('vm') @init('com.example.MyViewModel')">
                    <grid model="@load(v<caret>m.list)"/>
                </window>""");
        
        PsiElement target = getNavigationTarget();
        Assertions.assertNotNull(target);
        Assertions.assertTrue(target instanceof PsiClass);
        Assertions.assertEquals("com.example.MyViewModel", ((PsiClass)target).getQualifiedName());
    }

    // --- Helper Methods ---

    private void setupViewModelClasses() {
        myFixture.addFileToProject("com/example/CrewModel.java", """
                package com.example;
                public class CrewModel {
                    public String getName() { return null; }
                    public int getAge() { return 0; }
                }""");

        myFixture.addFileToProject("com/example/MyViewModel.java", """
                package com.example;
                import java.util.List;
                public class MyViewModel {
                    public List<String> getList() { return null; }
                    public String getName() { return null; }
                    public boolean isActive() { return false; }
                    public CrewModel getCrew() { return null; }
                }""");
    }

    private void verifyNavigation(String expectedMethodName) {
        verifyNavigation(expectedMethodName, "com.example.MyViewModel");
    }

    private void verifyNavigation(String expectedMethodName, String expectedClassName) {
        PsiElement target = getNavigationTarget();
        Assertions.assertNotNull(target, "Navigation target should not be null");
        Assertions.assertTrue(target instanceof PsiMethod, "Target should be a method");
        PsiMethod method = (PsiMethod) target;
        Assertions.assertEquals(expectedMethodName, method.getName());
        Assertions.assertNotNull(method.getContainingClass());
        Assertions.assertEquals(expectedClassName, method.getContainingClass().getQualifiedName());
    }

    private PsiElement getNavigationTarget() {
        return EdtTestUtil.runInEdtAndGet(() -> {
            return ApplicationManager.getApplication().runReadAction((com.intellij.openapi.util.Computable<PsiElement>) () -> {
                PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
                ZulMVVMPropertyNavigationHandler handler = new ZulMVVMPropertyNavigationHandler();
                PsiElement[] targets = handler.getGotoDeclarationTargets(element, myFixture.getCaretOffset(), myFixture.getEditor());
                return (targets != null && targets.length > 0) ? targets[0] : null;
            });
        });
    }
}
