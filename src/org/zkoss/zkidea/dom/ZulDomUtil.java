/* ZulDomUtil.java

	Purpose:
		
	Description:
		
	History:
		Jul 10 5:48 PM 2015, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.

*/
package org.zkoss.zkidea.dom;

import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.zkoss.zkidea.lang.ZulFileType;

/**
 * @author by jumperchen
 */
public class ZulDomUtil {
	public static boolean isZKFile(PsiFile file) {
		if(!(file instanceof XmlFile)) {
			return false;
		} else {
			String name = file.getName();
			return name.endsWith(ZulFileType.EXTENSION);
		}
	}
}
