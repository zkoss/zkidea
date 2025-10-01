package org.zkoss.zkidea.feedback;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public class DocumentationAction extends DumbAwareAction {

    private static final String DOCUMENTATION_URL = "https://docs.zkoss.org/zk_dev_ref/";

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        BrowserUtil.browse(DOCUMENTATION_URL);
    }
}
