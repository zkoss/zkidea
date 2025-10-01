package org.zkoss.zkidea.feedback;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public class ReportBugAction extends DumbAwareAction {

    private static final String BUG_REPORT_URL = "https://tracker.zkoss.org/";

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        BrowserUtil.browse(BUG_REPORT_URL);
    }
}
