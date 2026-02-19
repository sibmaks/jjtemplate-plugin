package io.github.sibmaks.jjtemplate.idea.actions;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ui.Messages;
import io.github.sibmaks.jjtemplate.idea.lang.JjtemplateFileType;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

public final class CopyMinifiedJsonAction extends AnAction {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        var project = event.getProject();
        var editor = event.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) {
            return;
        }

        try {
            var parsed = MAPPER.readTree(editor.getDocument().getText());
            CopyPasteManager.getInstance().setContents(new StringSelection(MAPPER.writeValueAsString(parsed)));
        } catch (Exception exception) {
            Messages.showErrorDialog(project, "Invalid JSON in active file:\n" + rootMessage(exception), "JJTemplate");
        }
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        var project = event.getProject();
        var editor = event.getData(CommonDataKeys.EDITOR);
        var file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        var visible = project != null && editor != null && file != null && isJjtemplate(file.getExtension());
        event.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private static boolean isJjtemplate(String extension) {
        if (extension == null) {
            return false;
        }
        return "jjt".equalsIgnoreCase(extension)
                || "jjtemplate".equalsIgnoreCase(extension)
                || JjtemplateFileType.INSTANCE.getDefaultExtension().equalsIgnoreCase(extension);
    }

    private static @NotNull String rootMessage(@NotNull Throwable throwable) {
        var cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        var message = cursor.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getMessage();
        }
        return (message == null || message.isBlank()) ? cursor.getClass().getSimpleName() : message;
    }
}
