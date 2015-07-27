/* MVVMAnnotationCompletionProvider.java

	Purpose:
		
	Description:
		
	History:
		12:46 PM 7/24/15, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.zkidea.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import org.zkoss.zkidea.dom.ZulDomUtil;

import java.util.Arrays;
import java.util.List;

/**
 * @author jumperchen
 */
public class MVVMAnnotationCompletionProvider extends CompletionContributor {
	@Override
	public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
		PsiFile psiFile = parameters.getOriginalFile();
		if(psiFile instanceof XmlFile) {
			if (ZulDomUtil.isZKFile(psiFile)) {
				PsiElement element = parameters.getPosition();
				if (ZulDomUtil.hasViewModel(element)) {
					String text = element.getText();
					PsiElement xmlText = element.getParent();
					if (xmlText instanceof XmlAttributeValue) {
						// result.stopHere();
						final String name = ((XmlAttribute) xmlText.getParent()).getName();
						final String value = element.getText();
						String query =  value.replace(CompletionUtilCore.DUMMY_IDENTIFIER, "");
						String[] strings = query.split(" ");
						if (strings.length > 0) {
							query = strings[strings.length - 1];
						}
						List<String> queryList = Arrays.asList(strings);
						CompletionResultSet completionResultSet = result.withPrefixMatcher(query);

						// TODO: support idspace resolver.
						// get annotation value
						String annotVal = "";
						int end = value.indexOf(CompletionUtilCore.DUMMY_IDENTIFIER);
						if (end > 0) {
							annotVal = value.substring(0, end);
						}
						String[] split = annotVal.split(" ");
						annotVal = split[split.length - 1];

						if (ZulDomUtil.VIEW_MODEL.equals(name)) {
							for (String annot : new String[] {"@id", "@init"}) {
								addElement(completionResultSet, queryList, annot);
							}
						} else {

							// event type
							if (name != null && name.startsWith("on")) {
								addElement(completionResultSet, queryList, "@command");
								addElement(completionResultSet, queryList, "@global-command");
							} else {
								addElement(completionResultSet, queryList, "@init");
								addElement(completionResultSet, queryList, "@load");
								addElement(completionResultSet, queryList, "@bind");
								addElement(completionResultSet, queryList, "@save");
								addElement(completionResultSet, queryList, "@ref");
								addElement(completionResultSet, queryList, "@converter");
								addElement(completionResultSet, queryList, "@validator");
								addElement(completionResultSet, queryList, "@template");
							}
						}
					}
				}
			}
		}
	}
	private boolean addElement(CompletionResultSet resultSet, List<String> queryList, String annotation) {
		for (String query : queryList)
			if (query.startsWith(annotation))
				return false;

		resultSet.addElement(LookupElementBuilder.create(annotation + "()"));
		return true;
	}
}
