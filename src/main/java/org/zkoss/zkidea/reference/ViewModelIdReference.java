package org.zkoss.zkidea.reference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reference for the ViewModel identifier (e.g., "vm" in "@load(vm.property)").
 * Resolves to the ViewModel PsiClass declared via @init.
 */
public class ViewModelIdReference extends PsiReferenceBase<XmlAttributeValue> {
    private static final Logger LOG = Logger.getInstance(ViewModelIdReference.class);

    private final PsiClass viewModelClass;

    public ViewModelIdReference(XmlAttributeValue element, TextRange rangeInElement,
                                PsiClass viewModelClass) {
        super(element, rangeInElement);
        this.viewModelClass = viewModelClass;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        LOG.debug("ViewModelIdReference.resolve: → "
                + (viewModelClass != null ? viewModelClass.getQualifiedName() : "null"));
        return viewModelClass;
    }

    @Override
    public Object @NotNull [] getVariants() {
        return EMPTY_ARRAY;
    }

    /**
     * No-op: renaming the ViewModel class must not change the {@code @id} alias
     * in the ZUL file — the alias is user-defined and independent of the class name.
     */
    @Override
    public PsiElement handleElementRename(@NotNull String newElementName)
            throws IncorrectOperationException {
        LOG.debug("ViewModelIdReference.handleElementRename: ignoring rename to '" + newElementName + "'");
        return getElement();
    }
}
