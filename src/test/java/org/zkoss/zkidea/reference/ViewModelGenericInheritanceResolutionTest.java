package org.zkoss.zkidea.reference;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * IntelliJ Platform test for resolving ViewModel binding chains whose intermediate
 * getter is inherited from a <em>generic</em> base class.
 *
 * <h3>Why BasePlatformTestCase</h3>
 * The behaviour under test is generic type-argument substitution across an inheritance
 * chain ({@code TypeConversionUtil.getSuperClassSubstitutor}). That cannot be reproduced
 * with Mockito mocks of {@code PsiClass}/{@code PsiType}; it needs real PSI built from
 * actual Java sources, which {@code BasePlatformTestCase} provides (with the
 * {@code com.intellij.java} plugin loaded).
 *
 * <h3>Scenario</h3>
 * <pre>
 *   class CrewModel        { String getName(); }
 *   abstract class GenericVM&lt;T&gt; { T getModel(); }     // getter declared on the generic base
 *   class CrewVM extends GenericVM&lt;CrewModel&gt; { }      // binds T = CrewModel
 * </pre>
 * In the ZUL, {@code @load(vm.model.name)} must resolve {@code name} to
 * {@code CrewModel.getName()}: {@code getModel()} returns the type variable {@code T},
 * which is bound to {@code CrewModel} by {@code CrewVM}.
 *
 * <p>Before the fix the chain walker read {@code getModel()}'s return type as the bare
 * type variable {@code T}, failed to resolve it to a class, and reported {@code name}
 * (and everything after {@code model}) as unresolved (red). Feature file:
 * {@code binding_property_navigation.feature}.
 */
public class ViewModelGenericInheritanceResolutionTest extends LightJavaCodeInsightFixtureTestCase {

    private void addViewModelHierarchy() {
        myFixture.addClass(
                "package com.example;\n" +
                "public class CrewModel {\n" +
                "    public String getName() { return null; }\n" +
                "}\n");
        myFixture.addClass(
                "package com.example;\n" +
                "public abstract class GenericVM<T> {\n" +
                "    public T getModel() { return null; }\n" +
                "}\n");
        myFixture.addClass(
                "package com.example;\n" +
                "public class CrewVM extends GenericVM<CrewModel> {\n" +
                "}\n");
    }

    /**
     * {@code vm.model.name} resolves to {@code CrewModel.getName()} even though
     * {@code getModel()} is inherited from {@code GenericVM<T>} and returns the
     * type variable {@code T} (bound to {@code CrewModel} by {@code CrewVM}).
     */
    public void testInheritedGenericGetterResolvesConcreteElementProperty() {
        addViewModelHierarchy();
        myFixture.configureByText("test.zul",
                "<div apply=\"org.zkoss.bind.BindComposer\"\n" +
                "     viewModel=\"@id('vm') @init('com.example.CrewVM')\">\n" +
                "  <label value=\"@load(vm.model.na<caret>me)\"/>\n" +
                "</div>\n");

        PsiReference ref = myFixture.getReferenceAtCaretPosition();
        assertNotNull("expected a reference on the 'name' segment", ref);

        PsiElement target = ref.resolve();
        assertNotNull(
                "vm.model.name must resolve: getModel() returns T, bound to CrewModel "
                        + "via CrewVM extends GenericVM<CrewModel>, so name -> CrewModel.getName()",
                target);
        assertTrue("resolved target should be a method", target instanceof PsiMethod);
        assertEquals("getName", ((PsiMethod) target).getName());
    }

    /**
     * A genuinely non-existent property on the substituted concrete type still does
     * NOT resolve — the diagnostic red highlight is intentionally preserved so that
     * unsupported bindings remain visible.
     */
    public void testUnknownPropertyOnSubstitutedTypeStaysUnresolved() {
        addViewModelHierarchy();
        myFixture.configureByText("test.zul",
                "<div apply=\"org.zkoss.bind.BindComposer\"\n" +
                "     viewModel=\"@id('vm') @init('com.example.CrewVM')\">\n" +
                "  <label value=\"@load(vm.model.bo<caret>gus)\"/>\n" +
                "</div>\n");

        PsiReference ref = myFixture.getReferenceAtCaretPosition();
        assertNotNull("expected a reference on the 'bogus' segment", ref);
        assertNull("vm.model.bogus must remain unresolved — CrewModel has no getBogus()",
                ref.resolve());
    }
}
