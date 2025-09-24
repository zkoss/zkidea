/* LangAddonSchemaProvider.java

	Purpose:
		Schema provider for ZK language addon configuration files (lang-addon.xml)

	Description:
		Provides XSD schema resources for lang-addon.xml files to enable
		code completion and validation in IntelliJ IDEA

	History:
		Created for ZK language addon configuration file support

Copyright (C) 2023 Potix Corporation. All Rights Reserved.

*/
package org.zkoss.zkidea.lang;

import com.intellij.javaee.ResourceRegistrar;
import com.intellij.javaee.StandardResourceProvider;

/**
 * Schema provider for ZK language addon configuration files (lang-addon.xml)
 * Enables code completion and validation for lang-addon.xml files
 *
 * @author Hawk
 */
public class LangAddonSchemaProvider implements StandardResourceProvider {

	// lang-addon schema
	public static final String LANG_ADDON_SCHEMA_URL = "http://www.zkoss.org/2005/zk/lang-addon";
	public static final String LANG_ADDON_PROJECT_SCHEMA_URL = "https://www.zkoss.org/2005/zk/lang-addon/lang-addon.xsd";//non-existed
	public static final String LANG_ADDON_PROJECT_SCHEMA_PATH = "org/zkoss/zkidea/lang/resources/lang-addon.xsd";

	public void registerResources(ResourceRegistrar registrar) {

		var path = "/" + LANG_ADDON_PROJECT_SCHEMA_PATH;
		// lang-addon
		registrar.addStdResource("lang-addon", path, getClass());
		registrar.addStdResource(LANG_ADDON_SCHEMA_URL, path, getClass());
		registrar.addStdResource(LANG_ADDON_PROJECT_SCHEMA_URL, path, getClass());
	}
}