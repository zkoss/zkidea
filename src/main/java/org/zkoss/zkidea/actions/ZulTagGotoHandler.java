package org.zkoss.zkidea.actions;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.Nullable;
import org.zkoss.zkidea.dom.ZulDomUtil;

/**
 * Intercepts Ctrl+click on ZUL element tag names and attribute names to suppress
 * navigation to XSD schema definitions.
 *
 * <p>For element tags: navigates between matching start and end tag names
 * (like Eclipse's XML editor behavior). For self-closing tags and attributes:
 * returns an empty array to suppress XSD navigation entirely.</p>
 */
public class ZulTagGotoHandler implements GotoDeclarationHandler {
    private static final Logger LOG = Logger.getInstance(ZulTagGotoHandler.class);

    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(PsiElement sourceElement,
                                                              int offset, Editor editor) {
        if (sourceElement == null) return null;
        if (!ZulDomUtil.isZKFile(sourceElement.getContainingFile())) return null;

        if (!(sourceElement instanceof XmlToken)) return null;
        XmlToken token = (XmlToken) sourceElement;

        // Handle tag names: jump between start <-> end tag
        if (token.getTokenType() == XmlTokenType.XML_NAME && token.getParent() instanceof XmlTag) {
            XmlTag tag = (XmlTag) token.getParent();
            XmlToken matchingToken = findMatchingTagNameToken(tag, token);
            if (matchingToken != null) {
                LOG.debug("ZulTagGotoHandler: navigating from " + token.getText()
                        + " to matching tag token");
                return new PsiElement[]{matchingToken};
            }
            // Self-closing tag — navigate to self (effectively no-op)
            return new PsiElement[]{token};
        }

        // Handle attribute names: navigate to self (suppress XSD navigation)
        if (token.getTokenType() == XmlTokenType.XML_NAME && token.getParent() instanceof XmlAttribute) {
            return new PsiElement[]{token};
        }

        return null;
    }

    /**
     * Finds the matching tag name token: if the source is in the start tag, returns
     * the end tag name token, and vice versa.
     */
    @Nullable
    private static XmlToken findMatchingTagNameToken(XmlTag tag, XmlToken sourceToken) {
        XmlToken firstNameToken = null;
        XmlToken lastNameToken = null;

        for (PsiElement child = tag.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof XmlToken) {
                XmlToken t = (XmlToken) child;
                if (t.getTokenType() == XmlTokenType.XML_NAME) {
                    if (firstNameToken == null) {
                        firstNameToken = t;
                    }
                    lastNameToken = t;
                }
            }
        }

        if (firstNameToken == null || firstNameToken == lastNameToken) {
            // Self-closing tag or only one name token — no match
            return null;
        }

        // If clicked on the start tag name, go to end; if end, go to start
        if (sourceToken.equals(firstNameToken)) {
            return lastNameToken;
        } else if (sourceToken.equals(lastNameToken)) {
            return firstNameToken;
        }

        return null;
    }
}