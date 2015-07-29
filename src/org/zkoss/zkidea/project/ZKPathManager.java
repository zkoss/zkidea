/* ZKPathManager.java

	Purpose:
		
	Description:
		
	History:
		9:11 AM 7/29/15, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.zkidea.project;

import java.io.File;

import com.intellij.openapi.application.PathManager;

/**
 * @author jumperchen
 */
public class ZKPathManager {
	public static String PLUGIN_NAME = "zkidea";

	public static String getPluginTempPath() {
		return PathManager.getPluginTempPath() + File.separator + PLUGIN_NAME;
	}

	public static String getPluginResourcePath(String classPath) {
		if (classPath.startsWith("/") || classPath.startsWith("\\"))
			return getPluginTempPath() + File.separator + "classes" + classPath;
		return  getPluginTempPath() + File.separator + "classes" + File.separator + classPath;
	}
}
