package io.github.sibmaks.jjtemplate.idea.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import io.github.sibmaks.jjtemplate.idea.docs.BuiltInFunctionIndex;
import io.github.sibmaks.jjtemplate.idea.lang.JjtemplateLanguage;
import io.github.sibmaks.jjtemplate.lexer.api.Keyword;
import org.jetbrains.annotations.NotNull;

public final class JjtemplateCompletionContributor extends CompletionContributor {
    public JjtemplateCompletionContributor() {
        extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(JjtemplateLanguage.INSTANCE),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        for (var keyword : Keyword.values()) {
                            result.addElement(
                                    LookupElementBuilder.create(keyword.getLexem())
                                            .withTypeText("keyword", true)
                            );
                        }

                        for (var function : BuiltInFunctionIndex.list()) {
                            result.addElement(
                                    LookupElementBuilder.create(function.presentableName())
                                            .withTypeText("built-in function", true)
                            );
                        }
                    }
                }
        );
    }
}
