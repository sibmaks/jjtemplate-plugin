package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;
import org.jetbrains.annotations.NotNull;

public final class JjtemplatePostFormatProcessor implements PostFormatProcessor {
    @Override
    public @NotNull PsiElement processElement(@NotNull PsiElement source, @NotNull CodeStyleSettings settings) {
        return source;
    }

    @Override
    public @NotNull TextRange processText(@NotNull PsiFile source,
                                          @NotNull TextRange rangeToReformat,
                                          @NotNull CodeStyleSettings settings) {
        if (!source.getLanguage().isKindOf(JjtemplateLanguage.INSTANCE)) {
            return rangeToReformat;
        }
        var document = getDocument(source);
        if (document == null) {
            return rangeToReformat;
        }

        var indentSize = settings.getIndentOptions(source.getFileType()).INDENT_SIZE;
        var before = document.getText();
        var after = JjtemplateJsonLikeFormatter.format(before, indentSize);
        if (before.equals(after)) {
            return rangeToReformat;
        }

        document.replaceString(0, before.length(), after);
        PsiDocumentManager.getInstance(source.getProject()).commitDocument(document);
        return TextRange.create(0, after.length());
    }

    private static Document getDocument(PsiFile source) {
        return PsiDocumentManager.getInstance(source.getProject()).getDocument(source);
    }
}
