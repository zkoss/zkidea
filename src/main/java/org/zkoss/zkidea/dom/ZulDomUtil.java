/* ZulDomUtil.java

	Purpose:
		
	Description:
		
	History:
		Jul 10 5:48 PM 2015, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.

*/
package org.zkoss.zkidea.dom;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.zkoss.zkidea.lang.ZulFileType;

/**
 * @author by jumperchen
 */
public class ZulDomUtil {
	public static String VIEW_MODEL = "viewModel";
	public static boolean isZKFile(PsiFile file) {
		if(!(file instanceof XmlFile)) {
			return false;
		} else {
			String name = file.getName();
			return name.endsWith(ZulFileType.EXTENSION);
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
}
