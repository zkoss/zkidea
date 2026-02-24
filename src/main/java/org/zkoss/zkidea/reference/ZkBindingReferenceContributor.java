package org.zkoss.zkidea.reference;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;

/**
 * Registers the ZK binding reference provider for XmlAttributeValue elements.
 * This enables Ctrl+Click navigation and Ctrl+Space completion for
 * ViewModel property references in ZUL files.
 */
public class ZkBindingReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(XmlAttributeValue.class),
                new ZkBindingReferenceProvider());
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(XmlAttributeValue.class),
                new ZkTemplateUriReferenceProvider());
    }
}
