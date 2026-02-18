package io.github.sibmaks.jjtemplate.idea.completion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import io.github.sibmaks.jjtemplate.lexer.TemplateLexer;
import io.github.sibmaks.jjtemplate.lexer.api.Keyword;
import io.github.sibmaks.jjtemplate.lexer.api.Token;
import io.github.sibmaks.jjtemplate.lexer.api.TokenType;
import io.github.sibmaks.jjtemplate.idea.docs.BuiltInFunctionIndex;
import io.github.sibmaks.jjtemplate.idea.lang.JjtemplateLanguage;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class JjtemplateCompletionContributor extends CompletionContributor {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public JjtemplateCompletionContributor() {
        extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(JjtemplateLanguage.INSTANCE),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        var dotContext = resolveDotContext(parameters);
                        if (dotContext != null) {
                            addDotVariableCompletions(dotContext, result);
                            return;
                        }

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

    private static void addDotVariableCompletions(@NotNull DotContext dotContext,
                                                  @NotNull CompletionResultSet result) {
        for (var localVariable : dotContext.localVariables()) {
            result.addElement(
                    LookupElementBuilder.create(localVariable)
                            .withTypeText("local variable", true)
            );
        }
        for (var globalVariable : dotContext.recentGlobalVariables()) {
            result.addElement(
                    LookupElementBuilder.create(globalVariable)
                            .withTypeText("global variable", true)
            );
        }
    }

    private static DotContext resolveDotContext(@NotNull CompletionParameters parameters) {
        var document = parameters.getEditor().getDocument();
        var offset = parameters.getOffset();
        if (offset <= 0 || offset > document.getTextLength()) {
            return null;
        }
        var chars = document.getCharsSequence();
        if (chars.charAt(offset - 1) != '.') {
            return null;
        }

        var source = chars.toString();
        List<Token> tokens;
        try {
            tokens = new TemplateLexer(source).tokens();
        } catch (Throwable ignored) {
            return null;
        }

        var dotIndex = findTokenAt(tokens, offset - 1, TokenType.DOT);
        if (dotIndex < 0 || !isInsideTemplate(tokens, dotIndex)) {
            return null;
        }

        var localVariables = new LinkedHashSet<>(extractLocalDefinitions(source));
        var rangeBindings = collectRangeBindings(tokens, dotIndex);
        localVariables.addAll(rangeBindings);

        var recentGlobalVariables = collectRecentGlobalVariables(tokens, dotIndex, localVariables, rangeBindings);
        return new DotContext(localVariables, recentGlobalVariables);
    }

    private static int findTokenAt(List<Token> tokens, int targetOffset, TokenType tokenType) {
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.type != tokenType) {
                continue;
            }
            if (targetOffset >= token.start && targetOffset < token.end) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isInsideTemplate(List<Token> tokens, int tokenIndex) {
        var depth = 0;
        for (int i = 0; i <= tokenIndex; i++) {
            var tokenType = tokens.get(i).type;
            if (tokenType == TokenType.OPEN_EXPR || tokenType == TokenType.OPEN_COND || tokenType == TokenType.OPEN_SPREAD) {
                depth++;
            } else if (tokenType == TokenType.CLOSE && depth > 0) {
                depth--;
            }
        }
        return depth > 0;
    }

    private static Set<String> collectRangeBindings(List<Token> tokens, int upToTokenIndex) {
        var bindings = new LinkedHashSet<String>();
        for (int i = 0; i <= upToTokenIndex && i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.type != TokenType.KEYWORD || !Keyword.RANGE.eq(token.lexeme)) {
                continue;
            }
            var firstBindingIndex = findNextNonTextTokenIndex(tokens, i + 1, upToTokenIndex);
            if (firstBindingIndex < 0 || tokens.get(firstBindingIndex).type != TokenType.IDENT) {
                continue;
            }
            bindings.add(tokens.get(firstBindingIndex).lexeme);

            var maybeCommaIndex = findNextNonTextTokenIndex(tokens, firstBindingIndex + 1, upToTokenIndex);
            if (maybeCommaIndex < 0 || tokens.get(maybeCommaIndex).type != TokenType.COMMA) {
                continue;
            }
            var secondBindingIndex = findNextNonTextTokenIndex(tokens, maybeCommaIndex + 1, upToTokenIndex);
            if (secondBindingIndex >= 0 && tokens.get(secondBindingIndex).type == TokenType.IDENT) {
                bindings.add(tokens.get(secondBindingIndex).lexeme);
            }
        }
        return bindings;
    }

    private static Set<String> collectRecentGlobalVariables(List<Token> tokens,
                                                            int dotTokenIndex,
                                                            Set<String> localVariables,
                                                            Set<String> rangeBindings) {
        var globals = new LinkedHashSet<String>();
        for (int i = dotTokenIndex - 1; i >= 0; i--) {
            var token = tokens.get(i);
            if (token.type != TokenType.IDENT) {
                continue;
            }
            if (!isRootVariable(tokens, i, dotTokenIndex)) {
                continue;
            }
            if (localVariables.contains(token.lexeme) || rangeBindings.contains(token.lexeme)) {
                continue;
            }
            globals.add(token.lexeme);
        }
        return globals;
    }

    private static boolean isRootVariable(List<Token> tokens, int identIndex, int upToIndex) {
        var previous = findPreviousNonTextTokenIndex(tokens, identIndex - 1, upToIndex);
        if (previous < 0 || tokens.get(previous).type != TokenType.DOT) {
            return false;
        }
        var beforeDot = findPreviousNonTextTokenIndex(tokens, previous - 1, upToIndex);
        if (beforeDot < 0) {
            return true;
        }
        return tokens.get(beforeDot).type != TokenType.IDENT;
    }

    private static Token findNextNonTextToken(List<Token> tokens, int fromInclusive, int upToIndex) {
        var tokenIndex = findNextNonTextTokenIndex(tokens, fromInclusive, upToIndex);
        if (tokenIndex < 0) {
            return null;
        }
        return tokens.get(tokenIndex);
    }

    private static int findPreviousNonTextTokenIndex(List<Token> tokens, int fromInclusive, int upToIndex) {
        for (int i = Math.min(fromInclusive, upToIndex); i >= 0; i--) {
            if (tokens.get(i).type != TokenType.TEXT) {
                return i;
            }
        }
        return -1;
    }

    private static int findNextNonTextTokenIndex(List<Token> tokens, int fromInclusive, int upToIndex) {
        var end = Math.min(upToIndex, tokens.size() - 1);
        for (int i = Math.max(fromInclusive, 0); i <= end; i++) {
            if (tokens.get(i).type != TokenType.TEXT) {
                return i;
            }
        }
        return -1;
    }

    private static Set<String> extractLocalDefinitions(String source) {
        try {
            var script = MAPPER.readTree(source);
            var definitionsNode = script.get("definitions");
            if (definitionsNode == null || !definitionsNode.isArray()) {
                return Set.of();
            }
            var result = new LinkedHashSet<String>();
            var definitionItems = definitionsNode.elements();
            while (definitionItems.hasNext()) {
                var definitionNode = definitionItems.next();
                if (!definitionNode.isObject()) {
                    continue;
                }
                var fields = definitionNode.fieldNames();
                while (fields.hasNext()) {
                    var field = fields.next();
                    if (IDENTIFIER_PATTERN.matcher(field).matches()) {
                        result.add(field);
                        continue;
                    }
                    result.addAll(extractDefinitionNames(field));
                }
            }
            return result;
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private static Set<String> extractDefinitionNames(String definitionKey) {
        if (definitionKey == null || definitionKey.isBlank()) {
            return Set.of();
        }
        var names = new HashSet<String>();
        try {
            var tokens = new TemplateLexer(definitionKey).tokens();
            for (int i = 0; i < tokens.size(); i++) {
                var token = tokens.get(i);
                if (token.type != TokenType.IDENT) {
                    continue;
                }
                var next = findNextNonTextToken(tokens, i + 1, tokens.size() - 1);
                if (next == null || next.type != TokenType.KEYWORD) {
                    continue;
                }
                if (Keyword.RANGE.eq(next.lexeme) || Keyword.SWITCH.eq(next.lexeme)) {
                    names.add(token.lexeme);
                }
            }
        } catch (Exception ignored) {
            return Set.of();
        }
        return names;
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

    private record DotContext(Set<String> localVariables, Set<String> recentGlobalVariables) {
    }

    private record NamespaceContext(String namespace, String functionPrefix) {
    }
}
