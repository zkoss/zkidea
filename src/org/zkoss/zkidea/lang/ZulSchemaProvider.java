/* ZulResourceProvider.java

	Purpose:
		
	Description:
		
	History:
		Jul 10 10:18 AM 2015, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.

*/
package org.zkoss.zkidea.lang;

import com.intellij.javaee.ResourceRegistrar;
import com.intellij.javaee.StandardResourceProvider;

/**
 * @author by jumperchen
 */
public class ZulSchemaProvider implements StandardResourceProvider {
	public static final String ZUL_PROJECT_SCHEMA_URL = "http://www.zkoss.org/2005/zul/zul.xsd";
	public static final String ZUL_PROJECT_SCHEMA_PATH = "/org/zkoss/zkidea/lang/resources/zul.xsd";

	public void registerResources(ResourceRegistrar registrar) {
		registrar.addStdResource(ZUL_PROJECT_SCHEMA_URL, ZUL_PROJECT_SCHEMA_PATH, getClass());
	}
}