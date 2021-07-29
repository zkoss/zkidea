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
import com.intellij.openapi.diagnostic.Logger;

/**
 * @author by jumperchen
 */
public class ZulSchemaProvider implements StandardResourceProvider {

	private static final Logger LOG = Logger.getInstance(
			ZulSchemaProvider.class);

	// zul schema
	public static final String ZUL_SCHEMA_URL = "http://www.zkoss.org/2005/zul";
	public static final String ZUL_PROJECT_SCHEMA_URL = "https://www.zkoss.org/2005/zul/zul.xsd";
	public static final String ZUL_PROJECT_SCHEMA_PATH = "org/zkoss/zkidea/lang/resources/zul.xsd";

	// zk schema
	public static final String ZK_SCHEMA_URL = "http://www.zkoss.org/2005/zk";

	// native schema
	public static final String NATIVE_SCHEMA_URL = "http://www.zkoss.org/2005/zk/native";

	// annotation schema
	public static final String ANNOTATION_SCHEMA_URL = "http://www.zkoss.org/2005/zk/annotation";

	// client schema
	public static final String CLIENT_SCHEMA_URL = "http://www.zkoss.org/2005/zk/client";

	// client attribute schema
	public static final String CLIENT_ATTRIBUTE_SCHEMA_URL = "http://www.zkoss.org/2005/zk/client/attribute";

	// xhtml schema
	public static final String XHTML_SCHEMA_URL = "http://www.w3.org/1999/xhtml";

	// xml schema
	public static final String XML_SCHEMA_URL = "http://www.zkoss.org/2007/xml";

	// shadow schema
	public static final String SHADOW_SCHEMA_URL = "http://www.zkoss.org/2015/shadow";

	public void registerResources(ResourceRegistrar registrar) {

		var path = "/" + ZUL_PROJECT_SCHEMA_PATH;
		// zul
		registrar.addStdResource("zul", path, getClass());
		registrar.addStdResource(ZUL_SCHEMA_URL, path, getClass());
		registrar.addStdResource(ZUL_PROJECT_SCHEMA_URL, path, getClass());

//		registrar.addStdResource("xml", ExternalResourceManagerEx.STANDARD_SCHEMAS + "XMLSchema.xsd", ExternalResourceManagerEx.class);
//		registrar.addStdResource(XML_SCHEMA_URL, ExternalResourceManagerEx.STANDARD_SCHEMAS + "XMLSchema.xsd", ExternalResourceManagerEx.class);
//
//
//		registrar.addStdResource("xhtml",
//				ExternalResourceManagerEx.STANDARD_SCHEMAS + "html5/xhtml5.xsd", ExternalResourceManagerEx.class);
//		registrar.addStdResource(XHTML_SCHEMA_URL,
//				ExternalResourceManagerEx.STANDARD_SCHEMAS + "html5/xhtml5.xsd", ExternalResourceManagerEx.class);
	}
}