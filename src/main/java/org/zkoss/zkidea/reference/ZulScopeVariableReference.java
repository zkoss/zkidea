package org.zkoss.zkidea.reference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reference from a scope variable name usage (e.g., {@code wrapper} in
 * {@code @load(wrapper.dto)}) back to its declaration {@link XmlAttribute}
 * ({@code <template var="wrapper">} or {@code <apply wrapper="...">} or
 * {@code forEachVar="wrapper"}).
 */
public class ZulScopeVariableReference extends PsiReferenceBase<XmlAttributeValue> {
    private static final Logger LOG = Logger.getInstance(ZulScopeVariableReference.class);

    private final XmlAttribute targetAttr;

    public ZulScopeVariableReference(XmlAttributeValue element, TextRange rangeInElement,
                                     XmlAttribute targetAttr) {
        super(element, rangeInElement);
        this.targetAttr = targetAttr;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        LOG.debug("ZulScopeVariableReference.resolve: → " + (targetAttr != null
                ? targetAttr.getName() : "null"));
        return targetAttr;
    }

    @Override
    public Object @NotNull [] getVariants() {
        return EMPTY_ARRAY;
    }
}
