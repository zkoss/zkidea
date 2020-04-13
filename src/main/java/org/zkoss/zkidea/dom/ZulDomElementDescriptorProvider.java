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
 * @author by jumperchen
 */
public class ZulDomElementDescriptorProvider implements XmlElementDescriptorProvider {
	public ZulDomElementDescriptorProvider() {
	}

	public XmlElementDescriptor getDescriptor(XmlTag tag) {
		return ZulDomElementDescriptorHolder.getInstance(tag.getProject()).getDescriptor(tag);
	}
}