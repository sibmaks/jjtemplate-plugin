package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class JjtemplateTypedHandler extends TypedHandlerDelegate {
    @Override
    public @NotNull Result beforeCharTyped(char c,
                                           @NotNull Project project,
                                           @NotNull Editor editor,
                                           @NotNull PsiFile file,
                                           @NotNull FileType fileType) {
        if (!file.getLanguage().isKindOf(JjtemplateLanguage.INSTANCE)) {
            return Result.CONTINUE;
        }
        if (editor.getSelectionModel().hasSelection()) {
            return Result.CONTINUE;
        }

        var close = switch (c) {
            case '"' -> '"';
            case '{' -> '}';
            case '[' -> ']';
            default -> 0;
        };
        if (close == 0) {
            return Result.CONTINUE;
        }

        var document = editor.getDocument();
        var offset = editor.getCaretModel().getOffset();
        var chars = document.getCharsSequence();
        if (c == '{' && offset > 0 && chars.charAt(offset - 1) == '{') {
            return Result.CONTINUE;
        }

        document.insertString(offset, String.valueOf(c) + close);
        editor.getCaretModel().moveToOffset(offset + 1);
        return Result.STOP;
    }
}
