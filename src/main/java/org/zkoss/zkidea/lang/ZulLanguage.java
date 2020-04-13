/* ZulLanguage.java

	Purpose:
		
	Description:
		
	History:
		Jul 10 10:18 AM 2015, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.

*/
package org.zkoss.zkidea.lang;

import com.intellij.lang.xml.XMLLanguage;

/**
 * @author by jumperchen
 */
public class ZulLanguage extends XMLLanguage {
	public static final ZulLanguage INSTANCE = new ZulLanguage();

	private ZulLanguage() {
		super(ZulLanguage.INSTANCE, "ZUL", new String[]{"text/html"});
	}
}