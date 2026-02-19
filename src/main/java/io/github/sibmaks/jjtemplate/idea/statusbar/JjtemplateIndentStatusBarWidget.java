package io.github.sibmaks.jjtemplate.idea.statusbar;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.util.Consumer;
import io.github.sibmaks.jjtemplate.idea.lang.JjtemplateFileType;
import io.github.sibmaks.jjtemplate.idea.toolwindow.JjtemplateIndentOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;
import java.awt.event.MouseEvent;

public final class JjtemplateIndentStatusBarWidget implements StatusBarWidget, StatusBarWidget.TextPresentation {
    public static final String WIDGET_ID = "JJTemplateIndent";

    private final Project project;
    private StatusBar statusBar;

    public JjtemplateIndentStatusBarWidget(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @NotNull String ID() {
        return WIDGET_ID;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
        this.statusBar = statusBar;
    }

    @Override
    public void dispose() {
        this.statusBar = null;
    }

    @Override
    public @Nullable WidgetPresentation getPresentation() {
        return this;
    }

    @Override
    public @NotNull String getText() {
        return "JJT Indent: " + JjtemplateIndentOptions.getIndent(project);
    }

    @Override
    public float getAlignment() {
        return Component.CENTER_ALIGNMENT;
    }

    @Override
    public @Nullable String getTooltipText() {
        return "JJTemplate indent used by Reformat File";
    }

    @Override
    public @Nullable Consumer<MouseEvent> getClickConsumer() {
        return event -> chooseIndent();
    }

    private void chooseIndent() {
        if (!hasOpenJjtemplateFile()) {
            Messages.showInfoMessage(project, "Open a JJTemplate file (*.jjt, *.jjtemplate) first.", "JJTemplate");
            return;
        }

        var current = JjtemplateIndentOptions.getIndent(project);
        var values = new String[]{"1", "2", "3", "4", "5", "6", "7", "8"};
        var selected = Messages.showChooseDialog(
                project,
                "Select indent size for JJTemplate files.",
                "JJTemplate Indent",
                null,
                values,
                String.valueOf(current)
        );
        if (selected < 0) {
            return;
        }
        JjtemplateIndentOptions.setIndent(project, Integer.parseInt(values[selected]));
        if (statusBar != null) {
            statusBar.updateWidget(ID());
        }
    }

    private boolean hasOpenJjtemplateFile() {
        var editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
            return false;
        }
        var file = FileDocumentManager.getInstance().getFile(editor.getDocument());
        return isJjtemplateFile(file);
    }

    private static boolean isJjtemplateFile(VirtualFile file) {
        if (file == null) {
            return false;
        }
        if (file.getFileType() == JjtemplateFileType.INSTANCE) {
            return true;
        }
        var extension = file.getExtension();
        return "jjt".equalsIgnoreCase(extension) || "jjtemplate".equalsIgnoreCase(extension);
    }
}
