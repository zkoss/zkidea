/* ZkXmlValidationAnnotator.java

	Purpose:
		Provides XML structure validation for ZK configuration files

	Description:
		Annotator that validates ZK XML files against their schemas,
		highlighting missing required elements and structural issues

	History:
		Created for enhanced ZK XML validation support

Copyright (C) 2023 Potix Corporation. All Rights Reserved.

*/
package org.zkoss.zkidea.lang;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.zkoss.zkidea.dom.ZulDomUtil;

/**
 * Custom XML annotator for ZK Framework files that provides structural validation
 * beyond what IntelliJ's built-in XML validation offers.
 *
 * This annotator specifically checks for required elements in lang-addon.xml
 * and highlights missing required elements that might not be caught by standard validation.
 */
public class ZkXmlValidationAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof XmlTag)) {
            return;
        }

        XmlTag tag = (XmlTag) element;

        // Only process lang-addon.xml or an XML file with lang-addon namespace
        if (!(tag.getContainingFile() instanceof XmlFile) ||
                (!ZulDomUtil.isLangAddonFile(tag.getContainingFile()) &&
                 !tag.getNamespace().equals(LangAddonSchemaProvider.LANG_ADDON_SCHEMA_URL))) {
            return;
        }

        validateLangAddonStructure(tag, holder);
    }

    private void validateLangAddonStructure(@NotNull XmlTag tag, @NotNull AnnotationHolder holder) {
        // Only validate root element
        if (!"language-addon".equals(tag.getName()) || tag.getParentTag() != null) {
            return;
        }

        // Check for required elements
        boolean hasAddonName = false;
        boolean hasLanguageName = false;

        XmlTag[] subTags = tag.getSubTags();
        for (XmlTag subTag : subTags) {
            String name = subTag.getName();
            if ("addon-name".equals(name)) {
                hasAddonName = true;
            } else if ("language-name".equals(name)) {
                hasLanguageName = true;
            }
        }

        // Report missing required elements
        if (!hasAddonName) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Missing required element: <addon-name>")
                    .range(tag.getTextRange())
                    .highlightType(ProblemHighlightType.GENERIC_ERROR)
                    .create();
        }

        if (!hasLanguageName) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Missing required element: <language-name>")
                    .range(tag.getTextRange())
                    .highlightType(ProblemHighlightType.GENERIC_ERROR)
                    .create();
        }
    }

}