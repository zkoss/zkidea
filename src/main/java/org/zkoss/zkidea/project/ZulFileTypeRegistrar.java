package org.zkoss.zkidea.project;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.*;
import org.zkoss.zkidea.lang.ZulFileType;

import java.util.List;

/**
 * Works around an IntelliJ bug where the *.zul file type association with XML
 * is lost after a plugin uninstall/reinstall cycle. (see <a href="https://github.com/zkoss/zkidea/issues/39">issue #39</a>)
 *
 * This activity checks on startup if the association is present and restores it if missing.
 * See: https://youtrack.jetbrains.com/issue/IJPL-39443
 */
public class ZulFileTypeRegistrar implements ProjectActivity {

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        // Defer to avoid running during startup indexing
        ApplicationManager.getApplication().invokeLater(() -> ensureZulFileTypeRegistered(project));
        return null;
    }

    private void ensureZulFileTypeRegistered(Project project) {
        FileTypeManager fileTypeManager = FileTypeManager.getInstance();
        FileType xmlFileType = XmlFileType.INSTANCE;

        List<FileNameMatcher> associations = fileTypeManager.getAssociations(xmlFileType);
        boolean isZulAssociated = false;
        for (FileNameMatcher matcher : associations) {
            if (matcher instanceof ExtensionFileNameMatcher && ZulFileType.EXTENSION.equals(((ExtensionFileNameMatcher) matcher).getExtension())) {
                isZulAssociated = true;
                break;
            }
            if ("*.zul".equals(matcher.getPresentableString())) {
                isZulAssociated = true;
                break;
            }
        }

        if (!isZulAssociated) {
            ApplicationManager.getApplication().runWriteAction(() -> {
                // Use the deprecated associate method as it's the safest way to ADD an association
                // without replacing all existing ones. This is a targeted fix for a specific bug.
                ((FileTypeManagerImpl) fileTypeManager).associate(xmlFileType, new ExtensionFileNameMatcher(ZulFileType.EXTENSION));
            });
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("news notification")
                    .createNotification(
                            "ZK Plugin associated ZUL file type",
                            "The '*.zul' pattern was automatically associated with the XML file type to work around a known IntelliJ bug.",
                            NotificationType.INFORMATION
                    )
                    .notify(project);
        }
    }
}
