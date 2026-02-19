package org.zkoss.zkidea.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.Nullable;

/**
 * Reference for the ViewModel identifier (e.g., "vm" in "@load(vm.property)").
 * Resolves to the ViewModel PsiClass declared via @init.
 */
public class ViewModelIdReference extends PsiReferenceBase<XmlAttributeValue> {
    private final PsiClass viewModelClass;

    public ViewModelIdReference(XmlAttributeValue element, TextRange rangeInElement, PsiClass viewModelClass) {
        super(element, rangeInElement);
        this.viewModelClass = viewModelClass;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        return viewModelClass;
    }

    @Override
    public Object[] getVariants() {
        return EMPTY_ARRAY;
    }
}
