/* ZKProjectsManager.java

	Purpose:
		
	Description:
		
	History:
		12:14 PM 7/24/15, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.zkidea.project;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.zkoss.zkidea.lang.ZulSchemaProvider;
import org.zkoss.zkidea.maven.ZKMavenArchetypesProvider;

/**
 * @author by jumperchen
 */
public class ZKProjectsManager extends AbstractProjectComponent {

	private static final Logger LOG = Logger.getInstance(ZKProjectsManager.class);

	private final AtomicBoolean isInitialized = new AtomicBoolean();

	protected ZKProjectsManager(Project project) {
		super(project);
	}

	public void initComponent() {
		StartupManagerEx startupManager = StartupManagerEx.getInstanceEx(this.myProject);
		startupManager.registerStartupActivity(new Runnable() {
			public void run() {
				ZKProjectsManager.this.doInit();
			}
		});
	}

	private void doInit() {
		synchronized (this.isInitialized) {
			if (!this.isInitialized.getAndSet(true)) {
				// fetch last zul.xsd file
				MavenUtil.runWhenInitialized(this.myProject, new DumbAwareRunnable() {
					public void run() {
						// TODO: support with project's ZK version
						updateZulSchema();
						updateMavenArchetype();
					}
				});
			}
		}
	}

	private void copyResourceToFile(String path, File toFile) {
		InputStream in = getClass().getClassLoader().getResourceAsStream(path);
		FileOutputStream out = null;
		try {
			if (!toFile.exists()) {
				// Bug fixed #7
				toFile.getParentFile().mkdirs();
				toFile.createNewFile();
			}

			out = new FileOutputStream(toFile);
			if (in != null) {
				FileUtil.copy(in, out);
			}
		} catch (Exception e) {
			LOG.error(e);
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
				}
			}
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private static boolean equals(Object a, Object b) {
		return (a == b) || (a != null && a.equals(b));
	}

	private void updateZulSchema() {
		try {
			final String pluginResourcePath = "file:" + ZKPathManager.getPluginResourcePath(ZulSchemaProvider.ZUL_PROJECT_SCHEMA_PATH);
			LOG.debug("PluginResourcePath: " + pluginResourcePath);

			ExternalResourceManager instance = ExternalResourceManager.getInstance();
			if (!equals(pluginResourcePath, instance.getResourceLocation(ZulSchemaProvider.ZUL_PROJECT_SCHEMA_PATH, ""))) {
				ApplicationManager.getApplication().runWriteAction(new Runnable() {
					@Override
					public void run() {
						ExternalResourceManager.getInstance().addResource(ZulSchemaProvider.ZUL_PROJECT_SCHEMA_URL, pluginResourcePath);
					}
				});
			}

			File fileSrc = new File(ZKPathManager.getPluginResourcePath(ZulSchemaProvider.ZUL_PROJECT_SCHEMA_PATH));
			if (!fileSrc.isFile() || fileSrc.length() < 20) {
				// copy from jar file.
				copyResourceToFile(ZulSchemaProvider.ZUL_PROJECT_SCHEMA_PATH, fileSrc);
			}
			if (fileSrc.lastModified() > new Date().getTime()) {
				return;// won't support this case
			}

			LOG.info("Downloading latest zul file: " + ZulSchemaProvider.ZUL_PROJECT_SCHEMA_URL);

			File fileTmp = new File(fileSrc.getAbsolutePath() + ".tmp");
			FileUtil.writeToFile(fileTmp, download(new URL(ZulSchemaProvider.ZUL_PROJECT_SCHEMA_URL)));
//			HttpRequests.request(ZulSchemaProvider.ZUL_PROJECT_SCHEMA_URL).saveToFile(fileTmp, ProgressManager.getGlobalProgressIndicator());
			double origin = getSchemaVersion(fileSrc);
			double newone = getSchemaVersion(fileTmp);
			if (newone > origin) {
				FileUtil.copy(fileTmp, fileSrc);
				fileTmp.deleteOnExit();
			}
			fileSrc.setLastModified(new Date().getTime() + 7 * 24 * 60 * 60 * 1000);
		} catch (IOException e) {
		}
	}
	private byte[] download(URL url) throws IOException {
		URLConnection uc = url.openConnection();
		int len = uc.getContentLength();
		InputStream is = new BufferedInputStream(uc.getInputStream());
		try {
			byte[] data = new byte[len];
			int offset = 0;
			while (offset < len) {
				int read = is.read(data, offset, data.length - offset);
				if (read < 0) {
					break;
				}
				offset += read;
			}
			if (offset < len) {
				throw new IOException(
						String.format("Read %d bytes; expected %d", offset, len));
			}
			return data;
		} finally {
			is.close();
		}
	}
	private void updateMavenArchetype() {
		try {
			File fileSrc = new File(ZKPathManager.getPluginResourcePath(ZKMavenArchetypesProvider.MAVEN_ARCHETYPE_PATH));
			if (!fileSrc.isFile() || fileSrc.length() < 20) {
				// copy from jar file.
				copyResourceToFile(ZKMavenArchetypesProvider.MAVEN_ARCHETYPE_PATH, fileSrc);
			}

			if (fileSrc.lastModified() > new Date().getTime()) {
				return;// won't support this case
			}
			LOG.info("Downloading latest maven archetype file: " + ZKMavenArchetypesProvider.MAVEN_ARCHETYPE_PATH);

			File fileTmp = new File(fileSrc.getAbsolutePath() + ".tmp");

			FileUtil.writeToFile(fileTmp, download(new URL(ZKMavenArchetypesProvider.MAVEN_ARCHETYPE_URL)));
//			HttpRequests.request(ZKMavenArchetypesProvider.MAVEN_ARCHETYPE_URL).saveToFile(fileTmp, ProgressManager.getGlobalProgressIndicator());

			if (fileTmp.length() > fileSrc.length()) {
				FileUtil.copy(fileTmp, fileSrc);
				fileTmp.deleteOnExit();
			}
			fileSrc.setLastModified(new Date().getTime() + 7 * 24 * 60 * 60 * 1000);
		} catch (IOException e) {
		}
	}

	private double getSchemaVersion(File file) {
		try {
			DocumentBuilderFactory instance = DocumentBuilderFactory.newInstance();
			//Using factory get an instance of document builder
			DocumentBuilder db = instance.newDocumentBuilder();

			//parse using builder to get DOM representation of the XML file
			Document parse = db.parse(file);
			NodeList elementsByTagName = parse.getElementsByTagName("xs:schema");
			if (elementsByTagName.getLength() > 0) {
				NamedNodeMap attributes = elementsByTagName.item(0).getAttributes();
				Node version = attributes.getNamedItem("version");
				if (version != null) {
					String nodeValue = version.getNodeValue().replace(".", "");
					return Double.parseDouble(nodeValue);
				}
			}
		}catch(Exception pce) {
			LOG.error(pce);
		}
		return 0;
	}

	public void disposeComponent() {
	}

	public void projectOpened() {
	}

	public void projectClosed() {
	}
}