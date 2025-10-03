/* WebBrowserUrlProvider.java

	Purpose:
		
	Description:
		
	History:
		4:56 PM 7/31/15, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.zkidea.editorActions;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.browsers.OpenInBrowserRequest;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.zkoss.zkidea.dom.ZulDomUtil;

/**
 * Provides a URL for ZUL files for the "Open in Browser" feature.
 * <p>
 * This class is responsible for generating the correct URL when a user right-clicks on a ZUL file
 * and selects "Open in Browser." It intelligently constructs the URL by analyzing the Maven `pom.xml`
 * to determine the web application's context path and server port.
 * <p>
 * Specifically, it parses the configuration of various Jetty Maven plugins to find the correct port and context path.
 * It also works in conjunction with {@link MavenRunnerPatcher} to get the port from a running Jetty instance,
 * ensuring that even dynamically assigned ports are handled correctly. This provides a seamless development
 * experience for ZK developers using Maven.
 *
 * @author jumperchen
 */
public class WebBrowserUrlProvider
		extends com.intellij.ide.browsers.WebBrowserUrlProvider {

	private static String MAVEN_STANDARD_WEBAPP_PATH = "src/main/webapp/";
	protected Url getUrl(@NotNull OpenInBrowserRequest request, @NotNull VirtualFile file) throws WebBrowserUrlProvider.BrowserException {
		Project project = request.getProject();
		MavenProjectsManager instance = MavenProjectsManager
				.getInstance(project);
		if (instance.isMavenizedProject()) {
			MavenProject project1 = instance.findProject(file);

			String port = "8080";

			FileType fileType = file.getFileType();
			if (ZulDomUtil.isZKFile(file.getExtension())) {
				ProcessHandler[] runningProcesses = ExecutionManager
						.getInstance(request.getProject())
						.getRunningProcesses();
				// PsiFile file1 = PsiManager.getInstance(project).findFile(file);
				MavenProject maven = instance
						.findContainingProject(file);
				if (maven != null) {
					String contextPath = maven.getMavenId().getArtifactId();
					String filePath = file.getPath();
					if (filePath.contains(MAVEN_STANDARD_WEBAPP_PATH)) {
						filePath = trim(filePath, MAVEN_STANDARD_WEBAPP_PATH);
					} else {
						MavenPlugin plugin = maven
								.findPlugin("org.mortbay.jetty",
										"jetty-maven-plugin");
						if (plugin != null) {
							for (Content content : plugin.getConfigurationElement().getContent()) {
								if (content instanceof Element) {
									Element element = (Element) content;
									if ("webAppSourceDirectory".equals(element.getName())) {
										String value = element.getValue();
										filePath = trim(filePath, value);
									} else if ("webAppConfig".equals(element.getName())) {
										for (Content c : element.getContent()) {
											if (c instanceof Element) {
												Element el = (Element) c;

												if ("contextPath".equals(el.getName())) {
													contextPath = el.getValue();
													break;
												}
											}
										}
									} else if ("webApp".equals(element.getName())) {
										for (Content c : element.getContent()) {
											if (c instanceof Element) {
												Element el = (Element) c;

												if ("contextPath".equals(el.getName())) {
													contextPath = el.getValue();
													break;
												}
											}
										}
									} else if ("connectors".equals(element.getName())) {
										for (Content c : element.getContent()) {
											if (c instanceof Element) {
												Element el = (Element) c;

												if ("connectors".equals(el.getName())) {
													for (Content c0 : element.getContent()) {
														if (c instanceof Element) {
															Element el0 = (Element) c0;

															if ("port".equals(el0.getName())) {
																port = el0.getValue();
																break;
															}
														}
													}
													break;
												}
											}
										}
									}
								}
							}
						} else {
							plugin = maven
									.findPlugin("org.eclipse.jetty",
											"jetty-maven-plugin");
							if (plugin != null) {
								for (Content content : plugin
										.getConfigurationElement().getContent()) {
									if (content instanceof Element) {
										Element element = (Element) content;
										if ("webAppSourceDirectory"
												.equals(element.getName())) {
											String value = element.getValue();
											filePath = trim(filePath, value);
										} else if ("webApp".equals(element.getName())) {
											for (Content c : element.getContent()) {
												if (c instanceof Element) {
													Element el = (Element) c;

													if ("contextPath".equals(el.getName())) {
														contextPath = el.getValue();
														break;
													}
												}
											}
										} else if ("httpConnector".equals(element.getName())) {
											for (Content c : element.getContent()) {
												if (c instanceof Element) {
													Element el = (Element) c;

													if ("port".equals(el.getName())) {
														port = el.getValue();
														break;
													}
												}
											}
										}
									}
								}
							} else {
								plugin = maven
										.findPlugin("org.mortbay.jetty",
												"maven-jetty-plugin");
								if (plugin != null) {
									for (Content content : plugin
											.getConfigurationElement().getContent()) {
										if (content instanceof Element) {
											Element element = (Element) content;
											if ("webAppSourceDirectory"
													.equals(element.getName())) {
												String value = element.getValue();
												filePath = trim(filePath, value);
											} else if ("webAppConfig".equals(element.getName())) {
												for (Content c : element.getContent()) {
													if (c instanceof Element) {
														Element el = (Element) c;

														if ("contextPath".equals(el.getName())) {
															contextPath = el.getValue();
															break;
														}
													}
												}
											}
										}
									}
								}
							}
						}
					}
					if (contextPath.startsWith("/"))
						contextPath = contextPath.substring(1);
					if (filePath.startsWith("/"))
						filePath = filePath.substring(1);
					if (System.getProperty("org.zkoss.zkidea.jetty.port." + maven.getFinalName()) != null)
						port = System.getProperty("org.zkoss.zkidea.jetty.port." + maven.getFinalName());
					return Urls.newHttpUrl("localhost:" + port + "/", contextPath + "/" + filePath);
				}
			}
		}
		return null;
	}

	private String trim(String filePath, String directory) {
		if (filePath.contains(directory)) {
			return filePath
					.substring(filePath.indexOf(directory) + (directory.length()));
		}
		return filePath;
	}
}
