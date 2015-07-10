/* ZulFileTypeFactory.java

	Purpose:
		
	Description:
		
	History:
		Jul 10 10:18 AM 2015, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.

*/
package org.zkoss.zkidea.lang;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author by jumperchen
 */
public class ZulFileTypeFactory extends FileTypeFactory {
	@Override
	public void createFileTypes(@NotNull FileTypeConsumer fileTypeConsumer) {
		fileTypeConsumer.consume(XmlFileType.INSTANCE, ZulFileType.EXTENSION);
	}
}
