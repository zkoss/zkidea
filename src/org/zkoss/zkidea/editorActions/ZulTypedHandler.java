/* ZulTypedHandler.java

	Purpose:
		
	Description:
		
	History:
		4:09 PM 7/24/15, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.zkidea.editorActions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * @author jumperchen
 */
public class ZulTypedHandler extends CompletionAutoPopupHandler {
	@Override
	public Result checkAutoPopup(char charTyped, Project project, Editor editor, PsiFile file) {
		if ("@".equals(String.valueOf(charTyped))) {
			AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
			return Result.STOP;
		} else {
			return super.checkAutoPopup(charTyped, project, editor, file);
		}
	}
}
