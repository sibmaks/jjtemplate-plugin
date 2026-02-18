package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.AutoPopupController;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public final class JjtemplateTypedHandler extends TypedHandlerDelegate {
    private static boolean isEscaped(@NotNull CharSequence chars, int quoteOffset) {
        var backslashes = 0;
        for (var i = quoteOffset - 1; i >= 0 && chars.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return (backslashes & 1) == 1;
    }

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

        if (c == '\'' && !isInSubstitution(editor)) {
            return Result.CONTINUE;
        }

        var close = switch (c) {
            case '"' -> '"';
            case '\'' -> '\'';
            case '{' -> '}';
            case '[' -> ']';
            default -> null;
        };
        if (close == null) {
            return Result.CONTINUE;
        }

        var document = editor.getDocument();
        var offset = editor.getCaretModel().getOffset();
        var chars = document.getCharsSequence();
        if (c == '"' && offset < chars.length() && chars.charAt(offset) == '"' && !isEscaped(chars, offset)) {
            editor.getCaretModel().moveToOffset(offset + 1);
            return Result.STOP;
        }
        if (c == '\'' && offset < chars.length() && chars.charAt(offset) == '\'' && !isEscaped(chars, offset)) {
            editor.getCaretModel().moveToOffset(offset + 1);
            return Result.STOP;
        }

        if (c == '"' && isEscaped(chars, offset)) {
            return Result.CONTINUE;
        }
        if (c == '\'' && isEscaped(chars, offset)) {
            return Result.CONTINUE;
        }

        if (c == '{' && offset > 0 && chars.charAt(offset - 1) == '{') {
            return Result.CONTINUE;
        }

        document.insertString(offset, String.valueOf(c) + close);
        editor.getCaretModel().moveToOffset(offset + 1);
        return Result.STOP;
    }

    @Override
    public @NotNull Result checkAutoPopup(char charTyped,
                                          @NotNull Project project,
                                          @NotNull Editor editor,
                                          @NotNull PsiFile file) {
        if (!file.getLanguage().isKindOf(JjtemplateLanguage.INSTANCE)) {
            return Result.CONTINUE;
        }
        if (charTyped == '.' && isInSubstitution(editor)) {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
            return Result.STOP;
        }
        return Result.CONTINUE;
    }

    private static boolean isInSubstitution(@NotNull Editor editor) {
        var lexer = new JjtemplateSyntaxLexer();
        var chars = editor.getDocument().getCharsSequence();
        lexer.start(chars, 0, chars.length(), 0);

        var offset = editor.getCaretModel().getOffset();
        var depth = 0;
        while (lexer.getTokenType() != null) {
            var tokenType = lexer.getTokenType();
            if (lexer.getTokenStart() >= offset) {
                break;
            }
            if (isTemplateOpen(tokenType)) {
                depth++;
            } else if (tokenType == JjtemplateTokenTypes.CLOSE && depth > 0) {
                depth--;
            }
            lexer.advance();
        }
        return depth > 0;
    }

    private static boolean isTemplateOpen(IElementType tokenType) {
        return tokenType == JjtemplateTokenTypes.OPEN_EXPR
                || tokenType == JjtemplateTokenTypes.OPEN_COND
                || tokenType == JjtemplateTokenTypes.OPEN_SPREAD;
    }
}
