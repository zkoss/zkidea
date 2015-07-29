/* ZKMavenArchetypesProvider.java

	Purpose:
		
	Description:
		
	History:
		6:08 PM 7/28/15, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.zkidea.maven;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.idea.maven.indices.MavenArchetypesProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.zkoss.zkidea.project.ZKPathManager;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author jumperchen
 */
public class ZKMavenArchetypesProvider implements MavenArchetypesProvider {


	private static final Logger LOG = Logger.getInstance(ZKMavenArchetypesProvider.class);
	public static final String MAVEN_ARCHETYPE_PATH = "/org/zkoss/zkidea/lang/resources/archetype-catalog.xml";
	public static final String MAVEN_ARCHETYPE_URL = "http://mavensync.zkoss.org/maven2/archetype-catalog.xml";

	@Override
	public Collection<org.jetbrains.idea.maven.model.MavenArchetype> getArchetypes() {
		File fileSrc = new File(ZKPathManager.getPluginResourcePath(ZKMavenArchetypesProvider.MAVEN_ARCHETYPE_PATH));

		if (!fileSrc.isFile()) {
			LOG.info(fileSrc + " is not found!");
			return Collections.EMPTY_LIST;
		}
		try {
			DocumentBuilderFactory instance = DocumentBuilderFactory.newInstance();
			//Using factory get an instance of document builder
			DocumentBuilder db = instance.newDocumentBuilder();

			//parse using builder to get DOM representation of the XML file
			Document parse = db.parse(fileSrc);
			NodeList archetypes = parse.getElementsByTagName("archetype");
			List list = new LinkedList<org.jetbrains.idea.maven.model.MavenArchetype>();
			for (int i = 0; i < archetypes.getLength(); i++) {
				Node item = archetypes.item(i);

				NodeList childNodes = item.getChildNodes();
				String groupId = null;
				String artifactId = null;
				String version = null;
				String description = null;
				for (int j = 0; j < childNodes.getLength(); j++) {
					Node item1 = childNodes.item(j);
					if ("groupId".equals(item1.getNodeName())) {
						groupId = item1.getFirstChild().getNodeValue();
					} else if ("artifactId".equals(item1.getNodeName())) {
						artifactId = item1.getFirstChild().getNodeValue();
					} else if ("version".equals(item1.getNodeName())) {
						version = item1.getFirstChild().getNodeValue();
					} else if ("description".equals(item1.getNodeName())) {
						description = item1.getFirstChild().getNodeValue();
					}
				}
				list.add(new org.jetbrains.idea.maven.model.MavenArchetype(groupId, artifactId, version, "", description));
			}
			return list;
		}catch(Exception pce) {
			LOG.error(pce);
		}
		return Collections.EMPTY_LIST;
	}
}
