package io.github.sibmaks.jjtemplate.idea.toolwindow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.content.ContentFactory;
import io.github.sibmaks.jjtemplate.compiler.api.TemplateCompiler;
import io.github.sibmaks.jjtemplate.compiler.api.TemplateScript;
import io.github.sibmaks.jjtemplate.idea.lang.JjtemplateFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public final class JjtemplateSideMenuToolWindowFactory implements ToolWindowFactory, DumbAware {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TemplateCompiler COMPILER = TemplateCompiler.getInstance();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        var content = ContentFactory.getInstance().createContent(createCompilerPanel(project), "Compiler", false);
        toolWindow.getContentManager().addContent(content);
    }

    private static @NotNull JPanel createCompilerPanel(@NotNull Project project) {
        try {
            var contextDocument = EditorFactory.getInstance().createDocument("{}");
            var contextInput = new EditorTextField(contextDocument, project, JsonFileType.INSTANCE, false, false);
            contextInput.setPreferredSize(new Dimension(320, 220));

            var panel = new JPanel(new BorderLayout(0, 8));
            panel.add(new JLabel("Context JSON"), BorderLayout.NORTH);
            panel.add(contextInput, BorderLayout.CENTER);

            var actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            var formatButton = new JButton("Format");
            formatButton.addActionListener(event -> formatContextJson(project, contextInput));
            var compileButton = new JButton("Compile");
            compileButton.addActionListener(event -> compileCurrentFile(project, contextInput));
            actionsPanel.add(formatButton);
            actionsPanel.add(compileButton);
            panel.add(actionsPanel, BorderLayout.SOUTH);
            return panel;
        } catch (Exception exception) {
            var fallback = new JPanel(new BorderLayout());
            fallback.add(new JLabel("Unable to initialize JJTemplate panel: " + getRootMessage(exception)), BorderLayout.NORTH);
            return fallback;
        }
    }

    private static void compileCurrentFile(@NotNull Project project, @NotNull EditorTextField contextInput) {
        try {
            var contextJson = formatContextJson(project, contextInput);
            if (contextJson == null) {
                return;
            }

            var editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (editor == null) {
                showError(project, "Open a JJTemplate file in the editor first.");
                return;
            }

            var document = editor.getDocument();
            var sourceFile = FileDocumentManager.getInstance().getFile(document);
            if (sourceFile == null || !isJjtemplateFile(sourceFile)) {
                showError(project, "Current file is not a JJTemplate file (*.jjt, *.jjtemplate).");
                return;
            }

            var script = MAPPER.readValue(document.getText(), TemplateScript.class);
            var compiled = COMPILER.compile(script);
            var context = MAPPER.readValue(contextJson, MAP_TYPE);
            var rendered = compiled.render(context);
            var output = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(rendered);

            var outputName = sourceFile.getNameWithoutExtension() + "-compiled.json";
            var outputFile = new LightVirtualFile(outputName, JsonFileType.INSTANCE, output);
            outputFile.setWritable(false);
            FileEditorManager.getInstance(project).openFile(outputFile, true, true);
        } catch (Exception exception) {
            showError(project, "Compilation failed:\n" + getRootMessage(exception));
        }
    }

    private static String formatContextJson(@NotNull Project project, @NotNull EditorTextField contextInput) {
        try {
            var parsed = MAPPER.readValue(contextInput.getText(), MAP_TYPE);
            var pretty = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
            contextInput.setText(pretty);
            return pretty;
        } catch (Exception exception) {
            showError(project, "Invalid context JSON:\n" + getRootMessage(exception));
            return null;
        }
    }

    private static boolean isJjtemplateFile(@NotNull VirtualFile file) {
        if (file.getFileType() == JjtemplateFileType.INSTANCE) {
            return true;
        }
        var extension = file.getExtension();
        return "jjt".equalsIgnoreCase(extension) || "jjtemplate".equalsIgnoreCase(extension);
    }

    private static void showError(@NotNull Project project, @NotNull String message) {
        Messages.showErrorDialog(project, message, "JJTemplate");
    }

    private static @NotNull String getRootMessage(@NotNull Throwable throwable) {
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
