/* ZulDomElementDescriptorProvider.java

	Purpose:
		
	Description:
		
	History:
		Jul 10 5:45 PM 2015, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.

*/
package org.zkoss.zkidea.dom;

import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;

/**
 * ZK-specific XML element descriptor provider that enables code completion and validation
 * for ZUL files and ZK configuration descriptor (zk.xml) in IntelliJ IDEA.
 *
 * <h3>How it works with IntelliJ Platform:</h3>
 * <ol>
 *   <li>IntelliJ Platform calls {@link #getDescriptor(XmlTag)} for each XML tag during parsing</li>
 *   <li>This method delegates to {@link ZkDomElementDescriptorHolder} to retrieve cached descriptors</li>
 *   <li>The holder provides XSD schema-based descriptors with default namespace support</li>
 *   <li>IntelliJ uses these descriptors to provide code completion, validation, and navigation</li>
 * </ol>
 *
 * <h3>Registration:</h3>
 * <p>This provider is registered in plugin.xml
 *
 * <h3>Supported Files:</h3>
 * <ul>
 *   <li>.zul files (ZK User Interface files)</li>
 *   <li>zk.xml files (ZK configuration files)</li>
 * </ul>
 *
 * @see XmlElementDescriptorProvider IntelliJ Platform interface for XML element descriptors
 * @see ZkDomElementDescriptorHolder Service that manages and caches element descriptors
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/xml-dom-api.html">IntelliJ Platform XML DOM API</a>
 * @see <a href="https://plugins.jetbrains.com/intellij-platform-explorer?extensions=com.intellij.xml.elementDescriptorProvider">IntelliJ Platform Explorer - XML Element Descriptor Provider</a>
 * @author jumperchen
 */
public class ZkDomElementDescriptorProvider implements XmlElementDescriptorProvider {
	public ZkDomElementDescriptorProvider() {
	}

	public XmlElementDescriptor getDescriptor(XmlTag tag) {
		return ZkDomElementDescriptorHolder.getInstance(tag.getProject()).getDescriptor(tag);
	}
}