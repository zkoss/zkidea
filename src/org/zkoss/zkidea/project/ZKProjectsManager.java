/* ZKProjectsManager.java

	Purpose:
		
	Description:
		
	History:
		12:14 PM 7/24/15, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.zkidea.project;

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.zkoss.zkidea.lang.ZulSchemaProvider;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

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
						try {
							LOG.info("Downloading latest zul file: " + ZulSchemaProvider.ZUL_PROJECT_SCHEMA_URL);
							File fileSrc = new File(System.getProperty("idea.plugins.path"), "zkidea/classes/" + ZulSchemaProvider.ZUL_PROJECT_SCHEMA_PATH);
							if (!fileSrc.isFile() || fileSrc.lastModified() > new Date().getTime()) {
								return;// won't support this case
							}
							File fileTmp = new File(fileSrc.getAbsolutePath() + ".tmp");
							HttpRequests.request(ZulSchemaProvider.ZUL_PROJECT_SCHEMA_URL).saveToFile(fileTmp, ProgressManager.getGlobalProgressIndicator());
							double origin = getSchemaVersion(fileSrc);
							double newone = getSchemaVersion(fileTmp);
							if (newone > origin) {
								FileUtil.copy(fileTmp, fileSrc);
								fileSrc.setLastModified(new Date().getTime() + 7 * 24 * 60 * 60 * 1000);
							}
						} catch (IOException e) {
						}

					}
				});
			}
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