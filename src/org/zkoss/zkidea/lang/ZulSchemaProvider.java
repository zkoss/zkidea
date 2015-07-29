/* ZulResourceProvider.java

	Purpose:
		
	Description:
		
	History:
		Jul 10 10:18 AM 2015, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.

*/
package org.zkoss.zkidea.lang;

import java.util.Objects;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.ResourceRegistrar;
import com.intellij.javaee.StandardResourceProvider;
import com.intellij.openapi.diagnostic.Logger;
import org.zkoss.zkidea.project.ZKPathManager;

/**
 * @author by jumperchen
 */
public class ZulSchemaProvider implements StandardResourceProvider {
	private static final Logger LOG = Logger.getInstance(ZulSchemaProvider.class);
	public static final String ZUL_PROJECT_SCHEMA_URL = "http://www.zkoss.org/2005/zul/zul.xsd";
	public static final String ZUL_PROJECT_SCHEMA_PATH = "/org/zkoss/zkidea/lang/resources/zul.xsd";

	public void registerResources(ResourceRegistrar registrar) {
		final String pluginResourcePath = "file:" + ZKPathManager.getPluginResourcePath(ZUL_PROJECT_SCHEMA_PATH);
		LOG.debug("PluginResourcePath: " + pluginResourcePath);

		ExternalResourceManager instance = ExternalResourceManager.getInstance();
		String resourceLocation = instance.getResourceLocation(ZUL_PROJECT_SCHEMA_URL, "");
		if (!Objects.equals(resourceLocation, pluginResourcePath)) {
			instance.addResource(ZUL_PROJECT_SCHEMA_URL, pluginResourcePath);
		}
		registrar.addStdResource(ZUL_PROJECT_SCHEMA_URL, ZUL_PROJECT_SCHEMA_PATH, getClass());
	}
}