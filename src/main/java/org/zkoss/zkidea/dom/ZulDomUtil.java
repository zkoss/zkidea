/* ZulDomUtil.java

	Purpose:
		
	Description:
		
	History:
		Jul 10 5:48 PM 2015, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.

*/
package org.zkoss.zkidea.dom;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;
import org.zkoss.zkidea.lang.ZulFileType;

/**
 * @author by jumperchen
 */
public class ZulDomUtil {
	public static String VIEW_MODEL = "viewModel";
	private static final Pattern ID_PATTERN = Pattern.compile("@id\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
	private static final Pattern INIT_PATTERN = Pattern.compile("@init\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
	public static boolean isZKFile(PsiFile file) {
		if(!(file instanceof XmlFile)) {
			return false;
		} else {
			String name = file.getName();
			return name.endsWith(ZulFileType.EXTENSION) || isZkConfigFile(file) || isLangAddonFile(file);
		}
	}

	public static boolean isZkConfigFile(PsiFile file) {
		if(!(file instanceof XmlFile)) {
			return false;
		} else {
			String name = file.getName();
			return "zk.xml".equals(name);
		}
	}

	public static boolean isLangAddonFile(PsiFile file) {
		if(!(file instanceof XmlFile)) {
			return false;
		} else {
			String name = file.getName();
			return "lang-addon.xml".equals(name);
		}
	}

	public static boolean isZKFile(String ext) {
		return ZulFileType.EXTENSION.equalsIgnoreCase(ext) || "zhtml".equalsIgnoreCase(ext);
	}
	public static boolean hasViewModel(PsiElement element) {
		do {
			if (element instanceof XmlTag) {
				if (hasAttribute((XmlTag) element, VIEW_MODEL)) {
					return true;
				}
			}
			element = element.getParent();
		} while (element != null);
		return false;
	}
	public static boolean hasAttribute(XmlTag tag, String key) {
		return tag.getAttribute(key) != null;
	}

	/**
	 * Finds the nearest ancestor XmlTag that has a viewModel attribute.
	 * @return the XmlTag with the viewModel attribute, or null if not found
	 */
	@Nullable
	public static XmlTag findViewModelTag(PsiElement element) {
		while (element != null) {
			if (element instanceof XmlTag) {
				if (hasAttribute((XmlTag) element, VIEW_MODEL)) {
					return (XmlTag) element;
				}
			}
			element = element.getParent();
		}
		return null;
	}

	/**
	 * Extracts the @id value from a viewModel attribute string.
	 * E.g., "@id('vm') @init('com.example.VM')" returns "vm".
	 */
	@Nullable
	public static String extractViewModelId(String viewModelAttrValue) {
		if (viewModelAttrValue == null) return null;
		Matcher matcher = ID_PATTERN.matcher(viewModelAttrValue);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}

	/**
	 * Extracts the class name from @init in a viewModel attribute string.
	 * E.g., "@id('vm') @init('com.example.VM')" returns "com.example.VM".
	 */
	@Nullable
	public static String extractViewModelClassName(String viewModelAttrValue) {
		if (viewModelAttrValue == null) return null;
		Matcher matcher = INIT_PATTERN.matcher(viewModelAttrValue);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}

	/**
	 * Resolves the ViewModel PsiClass from a viewModel attribute value.
	 */
	@Nullable
	public static PsiClass resolveViewModelClass(Project project, String viewModelAttrValue) {
		String className = extractViewModelClassName(viewModelAttrValue);
		if (className == null) return null;
		return JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
	}
}
