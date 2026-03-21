package org.zkoss.zkidea.dom;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fixes the {@code xs:any namespace="##other"} expansion bug that floods ZUL child-element
 * completion with every ZK component when the ZUL file has no explicit {@code xmlns} declaration.
 *
 * <h3>Root cause (confirmed by bytecode analysis of IntelliJ 2023.3)</h3>
 * {@code XmlElementDescriptorImpl.getElementsDescriptors(XmlTag context)} contains two
 * distinct code paths:
 * <ol>
 *   <li><b>context == null path (line 29):</b>
 *       calls {@code getElementsDescriptorsImpl(null)}, which collects only elements that are
 *       <em>explicitly declared</em> ({@code <xs:element ref="...">}) in the content model,
 *       and then skips the {@code xs:any} expansion block (lines 93–184) because context is null.</li>
 *   <li><b>context != null path:</b>
 *       after collecting explicit elements, calls
 *       {@code complexTypeDescriptor.canContainTag(context.getLocalName(),
 *       context.getNamespace(), context)}.
 *       For {@code xs:any namespace="##other"}, {@code canContainTag} returns {@code true}
 *       when {@code context.getNamespace()} is <em>different</em> from the schema's
 *       target namespace ({@code http://www.zkoss.org/2005/zul}).
 *       When the ZUL file has no {@code xmlns}, {@code context.getNamespace()} returns {@code ""},
 *       which is not equal to the ZUL namespace, so the condition is satisfied and every
 *       element registered in the schema ({@link XmlNSDescriptor#getRootElementsDescriptors})
 *       is merged into the result — flooding {@code <listbox>} completion with {@code <window>},
 *       {@code <button>}, etc.</li>
 * </ol>
 *
 * <h3>Fix</h3>
 * Override {@link #getElementsDescriptors(XmlTag)} to call
 * {@code delegate.getElementsDescriptors(null)}.  Passing {@code null} triggers
 * the safe code path: only explicitly-declared children from the XSD type definition
 * are returned; the {@code xs:any} expansion is skipped entirely.
 *
 * <p>All other {@link XmlElementDescriptor} methods are delegated unchanged.
 */
class ZulChildCompletionDescriptor implements XmlElementDescriptor {

    private final XmlElementDescriptor delegate;

    ZulChildCompletionDescriptor(@NotNull XmlElementDescriptor delegate) {
        this.delegate = delegate;
    }

    /**
     * Returns only the XSD-declared children of this element, bypassing the
     * {@code xs:any namespace="##other"} wildcard expansion that would otherwise
     * include every ZK component in the completion list.
     */
    @Override
    public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
        return delegate.getElementsDescriptors(null);
    }

    @Override public @Nullable String getQualifiedName()                              { return delegate.getQualifiedName(); }
    @Override public @Nullable String getDefaultName()                                { return delegate.getDefaultName(); }
    @Override public @Nullable XmlElementDescriptor getElementDescriptor(XmlTag c, XmlTag ctx) { return delegate.getElementDescriptor(c, ctx); }
    @Override public @NotNull XmlAttributeDescriptor[] getAttributesDescriptors(@Nullable XmlTag ctx) { return delegate.getAttributesDescriptors(ctx); }
    @Override public @Nullable XmlAttributeDescriptor getAttributeDescriptor(String name, @Nullable XmlTag ctx) { return delegate.getAttributeDescriptor(name, ctx); }
    @Override public @Nullable XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attr) { return delegate.getAttributeDescriptor(attr); }
    @Override public @Nullable XmlNSDescriptor getNSDescriptor()                      { return delegate.getNSDescriptor(); }
    @Override public @Nullable XmlElementsGroup getTopGroup()                         { return delegate.getTopGroup(); }
    @Override public int getContentType()                                             { return delegate.getContentType(); }
    @Override public @Nullable String getDefaultValue()                               { return delegate.getDefaultValue(); }
    @Override public @NotNull PsiElement getDeclaration()                             { return delegate.getDeclaration(); }
    @Override public String getName(PsiElement context)                               { return delegate.getName(context); }
    @Override public @Nullable String getName()                                       { return delegate.getName(); }
    @Override public void init(PsiElement element)                                    { delegate.init(element); }
}
