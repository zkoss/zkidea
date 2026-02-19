package org.zkoss.zkidea.reference;

import java.util.ArrayList;
import java.util.List;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiType;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.Nullable;

/**
 * Reference for a property segment in a ViewModel binding expression
 * (e.g., "filteredCrewList" in "vm.filteredCrewList").
 * Resolves to the corresponding getter method and provides completion variants.
 */
public class ViewModelPropertyReference extends PsiReferenceBase<XmlAttributeValue> {
    private final PsiClass ownerClass;
    private final String propertyName;

    public ViewModelPropertyReference(XmlAttributeValue element, TextRange rangeInElement,
                                       PsiClass ownerClass, String propertyName) {
        super(element, rangeInElement);
        this.ownerClass = ownerClass;
        this.propertyName = propertyName;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        if (ownerClass == null) return null;
        PsiMethod method = findGetter(ownerClass, propertyName);
        return method;
    }

    @Override
    public Object[] getVariants() {
        if (ownerClass == null) return EMPTY_ARRAY;
        List<LookupElement> variants = new ArrayList<>();
        for (PsiMethod method : ownerClass.getAllMethods()) {
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)) continue;
            if (method.getParameterList().getParametersCount() != 0) continue;
            String methodName = method.getName();
            String prop = getPropertyName(methodName);
            if (prop == null) continue;
            PsiType returnType = method.getReturnType();
            String typeText = returnType != null ? returnType.getPresentableText() : "";
            String containingClassName = method.getContainingClass() != null
                    ? method.getContainingClass().getName() : "";
            variants.add(LookupElementBuilder.create(prop)
                    .withIcon(AllIcons.Nodes.Property)
                    .withTypeText(typeText)
                    .withTailText("  (" + containingClassName + ")", true));
        }
        return variants.toArray();
    }

    /**
     * Finds the getter method for a property name on the given class.
     * Looks for getXxx() first, then isXxx().
     */
    @Nullable
    public static PsiMethod findGetter(PsiClass psiClass, String property) {
        if (psiClass == null || property == null || property.isEmpty()) return null;
        String capitalized = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        String getterName = "get" + capitalized;
        String boolGetterName = "is" + capitalized;
        for (PsiMethod method : psiClass.getAllMethods()) {
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)) continue;
            if (method.getParameterList().getParametersCount() != 0) continue;
            String name = method.getName();
            if (name.equals(getterName) || name.equals(boolGetterName)) {
                return method;
            }
        }
        return null;
    }

    /**
     * Extracts the property name from a getter method name.
     * "getFoo" -> "foo", "isFoo" -> "foo", "toString" -> null (not a property).
     */
    @Nullable
    private static String getPropertyName(String methodName) {
        String rest;
        if (methodName.startsWith("get") && methodName.length() > 3) {
            rest = methodName.substring(3);
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            rest = methodName.substring(2);
        } else {
            return null;
        }
        if (!Character.isUpperCase(rest.charAt(0))) return null;
        return Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
    }

    public PsiClass getOwnerClass() {
        return ownerClass;
    }
}
