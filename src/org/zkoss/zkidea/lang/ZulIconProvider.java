/* ZulIconProvider.java

	Purpose:
		
	Description:
		
	History:
		Jul 10 3:47 PM 2015, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.

*/
package org.zkoss.zkidea.lang;

import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author by jumperchen
 */
public class ZulIconProvider implements DumbAware, FileIconProvider {
	@Nullable
	@Override
	public Icon getIcon(@NotNull VirtualFile file, @Iconable.IconFlags int flags, @Nullable Project project) {
		if(project == null) return null;
		if (file.getExtension().equalsIgnoreCase(ZulFileType.EXTENSION))
			return ZulIcons.FILE;
		return null;
	}
}
