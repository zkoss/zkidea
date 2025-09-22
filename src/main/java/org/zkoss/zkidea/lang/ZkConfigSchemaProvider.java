/* ZkXmlSchemaProvider.java

	Purpose:
		Schema provider for ZK configuration files (zk.xml)

	Description:
		Provides XSD schema resources for zk.xml files to enable
		code completion and validation in IntelliJ IDEA

	History:
		Created for ZK XML configuration file support

Copyright (C) 2023 Potix Corporation. All Rights Reserved.

*/
package org.zkoss.zkidea.lang;

import com.intellij.javaee.ResourceRegistrar;
import com.intellij.javaee.StandardResourceProvider;

/**
 * Schema provider for ZK configuration files (zk.xml)
 * Enables code completion and validation for zk.xml files
 *
 * @author Hawk
 */
public class ZkConfigSchemaProvider implements StandardResourceProvider {

	// zk config schema
	public static final String ZK_CONFIG_SCHEMA_URL = "http://www.zkoss.org/2005/zk/config";
	public static final String ZK_CONFIG_PROJECT_SCHEMA_URL = "https://www.zkoss.org/2005/zk/config/zk.xsd";
	public static final String ZK_CONFIG_PROJECT_SCHEMA_PATH = "org/zkoss/zkidea/lang/resources/zk.xsd";

	public void registerResources(ResourceRegistrar registrar) {

		var path = "/" + ZK_CONFIG_PROJECT_SCHEMA_PATH;
		// zk config
		registrar.addStdResource("zk", path, getClass());
		registrar.addStdResource(ZK_CONFIG_SCHEMA_URL, path, getClass());
		registrar.addStdResource(ZK_CONFIG_PROJECT_SCHEMA_URL, path, getClass());
	}
}