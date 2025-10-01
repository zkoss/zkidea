package org.zkoss.zkidea.feedback;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public class NewsAction extends DumbAwareAction {

    private static final String URL = "https://www.zkoss.org/news/";

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        BrowserUtil.browse(URL);
    }
}
