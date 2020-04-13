/* MavenRunnerPatcher.java

	Purpose:
		
	Description:
		
	History:
		11:29 PM 7/31/15, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.zkidea.editorActions;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.runners.JavaProgramPatcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

/**
 * @author jumperchen
 */
public class MavenRunnerPatcher extends JavaProgramPatcher {
	private static String JETTY_ECLIPSE_PLUGIN_GROUPID = "org.eclipse.jetty";
	private static String JETTY_ECLIPSE_PLUGIN_ARTIFACTID = "jetty-maven-plugin";

	private static String JETTY_PLUGIN_GROUPID = "org.mortbay.jetty";
	private static String JETTY_PLUGIN_ARTIFACTID = JETTY_ECLIPSE_PLUGIN_ARTIFACTID;

	private static String JETTY_OLD_PLUGIN_GROUPID = JETTY_PLUGIN_GROUPID;
	private static String JETTY_OLD_PLUGIN_ARTIFACTID = "maven-jetty-plugin";

	private static String JBOSS_PLUGIN_GROUPID = "org.jboss.as.plugins";
	private static String JBOSS_PLUGIN_ARTIFACTID = "jboss-as-maven-plugin";

	private static String TOMCAT6_PLUGIN_GROUPID = "org.apache.tomcat.maven";
	private static String TOMCAT6_PLUGIN_ARTIFACTID = "tomcat6-maven-plugin";

	private static String TOMCAT7_PLUGIN_GROUPID = TOMCAT6_PLUGIN_GROUPID;
	private static String TOMCAT7_PLUGIN_ARTIFACTID = "tomcat7-maven-plugin";

	@Override public void patchJavaParameters(Executor executor,
			RunProfile runProfile, JavaParameters javaParameters) {
		if (runProfile instanceof MavenRunConfiguration) {
			MavenRunConfiguration config = (MavenRunConfiguration) runProfile;
			String projectName = null;
			Project project = config.getProject();

			VirtualFile file = null;
			try {
				file = VfsUtil.findFileByURL(
						new URL("file:" + javaParameters.getWorkingDirectory() + File.separator + "pom.xml"));
				MavenProject project1 = MavenProjectsManager
						.getInstance(project).findProject(file);
				if (project1 != null)
					projectName = project1.getFinalName();
			} catch (MalformedURLException e) {
			}

			for (String key : javaParameters
					.getProgramParametersList().getList()) {

				if (key.startsWith("-Djetty.port=")) {
					String port = key.substring(13);
					System.setProperty("org.zkoss.zkidea.jetty.port." + projectName, port);
					break;
				}
			}


			//			MavenRunConfiguration config = (MavenRunConfiguration) runProfile;
//			MavenRunnerParameters parameters = config.getRunnerParameters();
//			if (parameters.isPomExecution()) {
//				for (String goal : parameters.getGoals()) {
//					// for jetty
//					if (goal.contains("jetty:run") || goal.contains("jetty:start")) {
//						if (goal.contains(":run")) {
//							VirtualFile file = VirtualFileManager
//									.getInstance().findFileByUrl(
//											"file://" + parameters.getWorkingDirPath() + "/pom.xml");
//							PsiFile directory = PsiManager
//									.getInstance(config.getProject())
//									.findFile(file);
////							System.identityHashCode(file);
//							MavenProject project = MavenProjectsManager
//									.getInstance(directory.getProject())
//									.findProject(file);
//							Map<String, String> modelMap = project
//									.getModelMap();
////							for (MavenPlugin plugin: project.getPlugins()) {
////								plugin.get
////							}
//							MavenPlugin plugin = project
//									.findPlugin(JETTY_ECLIPSE_PLUGIN_GROUPID,
//											JETTY_ECLIPSE_PLUGIN_ARTIFACTID);
//							if (plugin == null) {
//								plugin = project.findPlugin(JETTY_PLUGIN_GROUPID,
//												JETTY_PLUGIN_ARTIFACTID);
//							}
//							if (plugin == null) {
//								plugin = project.findPlugin(JETTY_OLD_PLUGIN_GROUPID,
//										JETTY_OLD_PLUGIN_ARTIFACTID);
//							}
//							String propertyValue = javaParameters
//									.getProgramParametersList()
//									.getPropertyValue("jetty.port");
//							Element configurationElement = plugin
//									.getConfigurationElement();
//							for (Content content : configurationElement
//									.getContent()) {
//								((Element)content).getName();
//							}
//							System.out.println();
//						}
//					} else if (goal.contains("tomcat6:start")) {
//
//					} else if (goal.contains("tomcat7:start")) {
//
//					} else if (goal.contains("tomcat7:start")) {
//
//					} else if (goal.contains("jboss-as:start") || goal.contains("jboss-as:run")) {
//
//					}
//				}
//			}
		}
	}
}
