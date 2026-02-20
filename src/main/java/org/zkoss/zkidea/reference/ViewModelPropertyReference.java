package org.zkoss.zkidea.reference;

import java.util.ArrayList;
import java.util.List;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiType;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zkoss.zkidea.dom.ZulDomUtil;

/**
 * Reference for a property segment in a ViewModel binding expression
 * (e.g., "filteredCrewList" in "vm.filteredCrewList").
 * Resolves to the corresponding getter method and provides completion variants.
 * <p>
 * When {@code isCommandContext} is {@code true} (i.e., the reference sits inside
 * {@code @command(...)} or {@code @global-command(...)}), {@link #getVariants()}
 * returns {@code @Command}-annotated method names instead of getter properties.
 */
public class ViewModelPropertyReference extends PsiReferenceBase<XmlAttributeValue> {
    private static final Logger LOG = Logger.getInstance(ViewModelPropertyReference.class);

    private final PsiClass ownerClass;
    private final String propertyName;
    private final boolean isCommandContext;

    public ViewModelPropertyReference(XmlAttributeValue element, TextRange rangeInElement,
                                      PsiClass ownerClass, String propertyName) {
        this(element, rangeInElement, ownerClass, propertyName, false);
    }

    public ViewModelPropertyReference(XmlAttributeValue element, TextRange rangeInElement,
                                      PsiClass ownerClass, String propertyName,
                                      boolean isCommandContext) {
        super(element, rangeInElement);
        this.ownerClass = ownerClass;
        this.propertyName = propertyName;
        this.isCommandContext = isCommandContext;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        if (ownerClass == null) return null;
        PsiMethod method = ZulDomUtil.findGetter(ownerClass, propertyName);
        LOG.debug("ViewModelPropertyReference.resolve: property='" + propertyName
                + "' → " + (method != null ? method.getName() : "null"));
        return method;
    }

    @Override
    public Object @NotNull [] getVariants() {
        if (ownerClass == null) return EMPTY_ARRAY;

        if (isCommandContext) {
            return getCommandVariants();
        }
        return getPropertyVariants();
    }

    private Object[] getPropertyVariants() {
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
        LOG.debug("ViewModelPropertyReference.getPropertyVariants: " + variants.size()
                + " variants for " + ownerClass.getName());
        return variants.toArray();
    }

    private Object[] getCommandVariants() {
        List<LookupElement> variants = ZkCommandReference.buildCommandLookupElements(ownerClass);
        LOG.debug("ViewModelPropertyReference.getCommandVariants: " + variants.size()
                + " command variants for " + ownerClass.getName());
        return variants.toArray();
    }

    /**
     * Converts the renamed method name back to a property name and updates
     * the text range in the ZUL attribute value.
     * E.g., renaming {@code getFoo()} to {@code getBar()} updates {@code foo} → {@code bar}.
     */
    @Override
    public PsiElement handleElementRename(@NotNull String newElementName)
            throws IncorrectOperationException {
        String newPropName;
        if (newElementName.startsWith("get") && newElementName.length() > 3
                && Character.isUpperCase(newElementName.charAt(3))) {
            newPropName = Character.toLowerCase(newElementName.charAt(3))
                    + newElementName.substring(4);
        } else if (newElementName.startsWith("is") && newElementName.length() > 2
                && Character.isUpperCase(newElementName.charAt(2))) {
            newPropName = Character.toLowerCase(newElementName.charAt(2))
                    + newElementName.substring(3);
        } else {
            newPropName = newElementName;
        }
        LOG.debug("ViewModelPropertyReference.handleElementRename: '"
                + newElementName + "' → property='" + newPropName + "'");
        return ElementManipulators.handleContentChange(getElement(), getRangeInElement(), newPropName);
    }

    /**
     * Extracts the property name from a getter method name.
     * "getFoo" → "foo", "isFoo" → "foo", "toString" → null.
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
