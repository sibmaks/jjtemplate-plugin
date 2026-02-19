package io.github.sibmaks.jjtemplate.idea.toolwindow;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import io.github.sibmaks.jjtemplate.idea.lang.JjtemplateFileType;

public final class JjtemplateIndentOptions {
    private static final int DEFAULT_INDENT = 2;

    private JjtemplateIndentOptions() {
    }

    public static int getIndent(Project project) {
        var options = CodeStyle.getSettings(project).getIndentOptions(JjtemplateFileType.INSTANCE);
        if (options == null) {
            return DEFAULT_INDENT;
        }
        return normalize(options.INDENT_SIZE);
    }

    public static void setIndent(Project project, int indent) {
        var options = CodeStyle.getSettings(project).getIndentOptions(JjtemplateFileType.INSTANCE);
        if (options == null) {
            return;
        }
        var normalized = normalize(indent);
        options.INDENT_SIZE = normalized;
        options.CONTINUATION_INDENT_SIZE = normalized;
        options.TAB_SIZE = normalized;
        options.USE_TAB_CHARACTER = false;
        CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged();
    }

    public static int normalize(int indent) {
        return Math.max(1, Math.min(indent, 8));
    }
}
