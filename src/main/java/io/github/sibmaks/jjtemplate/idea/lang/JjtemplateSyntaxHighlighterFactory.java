package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import org.jetbrains.annotations.NotNull;

public final class JjtemplateSyntaxHighlighterFactory extends SingleLazyInstanceSyntaxHighlighterFactory {
    @Override
    protected @NotNull SyntaxHighlighter createHighlighter() {
        return new JjtemplateSyntaxHighlighter();
    }
}
