/* GotoJavaClassHandler.java

	Purpose:
		
	Description:
		
	History:
		5:54 PM 7/27/15, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.zkidea.actions;

import com.intellij.codeInsight.completion.JavaClassReferenceCompletionContributor;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import org.jetbrains.annotations.Nullable;

/**
 * @author jumperchen
 */
public class GotoJavaClassHandler implements GotoDeclarationHandler {
	@Nullable
	@Override
	public PsiElement[] getGotoDeclarationTargets(PsiElement psiElement, int offset, Editor editor) {
		if (psiElement == null || psiElement.getContainingFile() == null) {
			return null;
		}
		JavaClassReference reference = JavaClassReferenceCompletionContributor.findJavaClassReference(psiElement.getContainingFile(), offset);
		if (reference != null) {
			Object[] variants = reference.getVariants();
			if (variants != null && variants.length > 0) {
				String className = reference.getCanonicalText().trim();
				if (className.endsWith("\"") || className.endsWith("'"))
					className = className.substring(0, className.length()-1);
				LookupElement simulation = null;
				for (Object o : variants) {
					if (o instanceof LookupElement) {
						LookupElement element = (LookupElement) o;
						final String lookup = element.getLookupString();
						if (className.equals(lookup)) {
							return new PsiElement[]{element.getPsiElement()};
						}
						if (className.startsWith(lookup)) {
							if (simulation != null) {
								// more accurate
								if (simulation.getLookupString().length() < lookup.length()) {
									simulation = element;
								}
							} else {
								simulation = element;
							}
						}
					}
				}
				if (simulation != null) {
					return new PsiElement[]{simulation.getPsiElement()};
				}
			}
		}
		return null;
	}

	@Nullable
	@Override
	public String getActionText(DataContext dataContext) {
		return null;
	}
}
