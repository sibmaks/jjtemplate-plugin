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
                        var namespaceContext = resolveNamespaceContext(parameters);
                        for (var keyword : Keyword.values()) {
                            result.addElement(
                                    LookupElementBuilder.create(keyword.getLexem())
                                            .withTypeText("keyword", true)
                            );
                        }

                        if (namespaceContext == null) {
                            for (var function : BuiltInFunctionIndex.list()) {
                                result.addElement(
                                        LookupElementBuilder.create(function.presentableName())
                                                .withTypeText("built-in function", true)
                                );
                            }
                            return;
                        }

                        var namespaceResult = result.withPrefixMatcher(namespaceContext.functionPrefix());
                        for (var function : BuiltInFunctionIndex.list()) {
                            if (!namespaceContext.namespace().equals(function.namespace())) {
                                continue;
                            }
                            namespaceResult.addElement(
                                    LookupElementBuilder.create(function.presentableName())
                                            .withLookupString(function.name())
                                            .withTypeText("built-in function", true)
                            );
                        }
                    }
                }
        );
    }

    private static NamespaceContext resolveNamespaceContext(CompletionParameters parameters) {
        var document = parameters.getEditor().getDocument();
        var offset = parameters.getOffset();
        if (offset <= 0 || offset > document.getTextLength()) {
            return null;
        }
        var chars = document.getCharsSequence();
        var start = offset - 1;
        while (start >= 0 && isFunctionTokenChar(chars.charAt(start))) {
            start--;
        }
        var token = chars.subSequence(start + 1, offset).toString();
        var separator = token.indexOf("::");
        if (separator <= 0) {
            return null;
        }
        if (separator == token.length() - 2) {
            return new NamespaceContext(token.substring(0, separator), "");
        }
        return new NamespaceContext(token.substring(0, separator), token.substring(separator + 2));
    }

    private static boolean isFunctionTokenChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == ':';
    }

    private record NamespaceContext(String namespace, String functionPrefix) {
    }
}
