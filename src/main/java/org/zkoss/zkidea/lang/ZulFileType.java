/* ZulFileType.java

	Purpose:
		
	Description:
		
	History:
		Jul 10 10:27 AM 2015, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.

*/
package org.zkoss.zkidea.lang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author by jumperchen
 */
public class ZulFileType extends LanguageFileType {
	public static final ZulFileType INSTANCE = new ZulFileType();

	public static final String EXTENSION = "zul";
	private ZulFileType() {
		super(ZulLanguage.INSTANCE);
	}

	@NotNull
	@Override
	public String getName() {
		return "Zul file";
	}

	@NotNull
	@Override
	public String getDescription() {
		return "ZK UI Language file";
	}

	@NotNull
	@Override
	public String getDefaultExtension() {
		return "zul";
	}

	@Nullable
	@Override
	public Icon getIcon() {
		return ZulIcons.FILE;
	}
}
