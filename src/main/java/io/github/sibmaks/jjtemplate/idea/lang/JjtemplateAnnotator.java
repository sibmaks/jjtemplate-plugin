package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import io.github.sibmaks.jjtemplate.lexer.TemplateLexer;
import io.github.sibmaks.jjtemplate.lexer.api.TemplateLexerException;
import org.jetbrains.annotations.NotNull;

public final class JjtemplateAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof JjtemplateFile)) {
            return;
        }
        var text = element.getText();
        try {
            new TemplateLexer(text).tokens();
        } catch (TemplateLexerException e) {
            var position = Math.min(Math.max(e.getPosition(), 0), Math.max(text.length() - 1, 0));
            holder.newAnnotation(HighlightSeverity.ERROR, e.getMessage())
                    .range(TextRange.from(position, 1))
                    .create();
        }
    }
}
