package io.github.sibmaks.jjtemplate.idea.lang;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import io.github.sibmaks.jjtemplate.lexer.TemplateLexer;
import io.github.sibmaks.jjtemplate.lexer.api.Keyword;
import io.github.sibmaks.jjtemplate.lexer.api.TemplateLexerException;
import io.github.sibmaks.jjtemplate.lexer.api.Token;
import io.github.sibmaks.jjtemplate.lexer.api.TokenType;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class JjtemplateAnnotator implements Annotator {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static void highlightJsonLike(String text, AnnotationHolder holder) {
        var nesting = 0;
        var inString = false;
        var escaped = false;
        var stringStart = -1;
        var inTemplate = false;

        for (int i = 0; i < text.length(); i++) {
            var ch = text.charAt(i);

            if (inTemplate) {
                if (ch == '}' && i + 1 < text.length() && text.charAt(i + 1) == '}') {
                    inTemplate = false;
                    i++;
                }
                continue;
            }

            if (inString) {
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (ch == '\\') {
                    escaped = true;
                    continue;
                }
                if (ch == '"') {
                    inString = false;
                    if (isObjectKey(text, i + 1)) {
                        highlightStringWithLookups(text, stringStart, i + 1, holder, JjtemplateSyntaxHighlighter.OBJECT_KEY);
                    } else {
                        highlightStringWithLookups(text, stringStart, i + 1, holder, JjtemplateSyntaxHighlighter.JSON_STRING);
                    }
                }
                continue;
            }

            if (isTemplateStart(text, i)) {
                inTemplate = true;
                continue;
            }

            if (ch == '"') {
                inString = true;
                stringStart = i;
                continue;
            }
            if (ch == '{') {
                if (nesting == 0) {
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .range(TextRange.from(i, 1))
                            .textAttributes(JjtemplateSyntaxHighlighter.ROOT_OBJECT)
                            .create();
                }
                nesting++;
                continue;
            }
            if (ch == '[') {
                if (nesting == 0) {
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .range(TextRange.from(i, 1))
                            .textAttributes(JjtemplateSyntaxHighlighter.ROOT_ARRAY)
                            .create();
                }
                nesting++;
                continue;
            }
            if (ch == '}' || ch == ']') {
                if (nesting > 0) {
                    nesting--;
                }
                if (nesting == 0) {
                    var attributes = ch == '}' ? JjtemplateSyntaxHighlighter.ROOT_OBJECT : JjtemplateSyntaxHighlighter.ROOT_ARRAY;
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .range(TextRange.from(i, 1))
                            .textAttributes(attributes)
                            .create();
                }
                continue;
            }

            if (ch == '-' || Character.isDigit(ch)) {
                var end = readNumberEnd(text, i);
                if (end > i) {
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .range(TextRange.create(i, end))
                            .textAttributes(JjtemplateSyntaxHighlighter.JSON_NUMBER)
                            .create();
                    i = end - 1;
                    continue;
                }
            }

            if (Character.isLetter(ch)) {
                var end = readWordEnd(text, i);
                var word = text.substring(i, end);
                if ("true".equals(word) || "false".equals(word) || "null".equals(word)) {
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .range(TextRange.create(i, end))
                            .textAttributes(JjtemplateSyntaxHighlighter.JSON_BOOLEAN)
                            .create();
                }
                i = end - 1;
            }
        }
    }

    private static boolean isObjectKey(String text, int index) {
        var pointer = index;
        while (pointer < text.length() && Character.isWhitespace(text.charAt(pointer))) {
            pointer++;
        }
        return pointer < text.length() && text.charAt(pointer) == ':';
    }

    private static boolean isTemplateStart(String text, int index) {
        if (text.charAt(index) != '{' || index + 1 >= text.length()) {
            return false;
        }
        var next = text.charAt(index + 1);
        return next == '{' || next == '?' || next == '.';
    }

    private static void highlightStringWithLookups(String text,
                                                   int start,
                                                   int endExclusive,
                                                   AnnotationHolder holder,
                                                   TextAttributesKey plainTextStyle) {
        var contentStart = start + 1;
        var contentEnd = Math.max(contentStart, endExclusive - 1);
        var cursor = start;
        var index = contentStart;

        while (index < contentEnd) {
            if (!isTemplateStart(text, index)) {
                index++;
                continue;
            }
            var lookupEnd = findTemplateEnd(text, index, contentEnd);
            if (lookupEnd < 0) {
                break;
            }
            annotateRange(holder, cursor, index, plainTextStyle);
            highlightLookupTokens(text.substring(index, lookupEnd), index, holder);
            cursor = lookupEnd;
            index = lookupEnd;
        }
        annotateRange(holder, cursor, endExclusive, plainTextStyle);
    }

    private static int findTemplateEnd(String text, int start, int contentEnd) {
        for (int i = start + 2; i + 1 < contentEnd; i++) {
            if (text.charAt(i) == '}' && text.charAt(i + 1) == '}') {
                return i + 2;
            }
        }
        return -1;
    }

    private static void highlightLookupTokens(String lookup, int baseOffset, AnnotationHolder holder) {
        try {
            var tokens = new TemplateLexer(lookup).tokens();
            for (var token : tokens) {
                var key = mapLookupToken(token.type);
                if (key == null) {
                    continue;
                }
                annotateRange(holder, baseOffset + token.start, baseOffset + token.end, key);
            }
        } catch (Throwable ignored) {
            annotateRange(holder, baseOffset, baseOffset + lookup.length(), DefaultLanguageHighlighterColors.BRACES);
        }
    }

    private static TextAttributesKey mapLookupToken(TokenType tokenType) {
        return switch (tokenType) {
            case OPEN_EXPR, OPEN_COND, OPEN_SPREAD, CLOSE -> DefaultLanguageHighlighterColors.BRACES;
            case KEYWORD, BOOLEAN, NULL -> DefaultLanguageHighlighterColors.KEYWORD;
            case STRING -> DefaultLanguageHighlighterColors.STRING;
            case NUMBER -> DefaultLanguageHighlighterColors.NUMBER;
            case PIPE, COLON, QUESTION -> DefaultLanguageHighlighterColors.OPERATION_SIGN;
            case DOT -> DefaultLanguageHighlighterColors.DOT;
            case COMMA -> DefaultLanguageHighlighterColors.COMMA;
            case LPAREN, RPAREN -> DefaultLanguageHighlighterColors.PARENTHESES;
            case IDENT -> null;
            case TEXT -> null;
        };
    }

    private static void validateJson(String text, AnnotationHolder holder) {
        if (text.isBlank()) {
            return;
        }
        try (var parser = new JsonFactory().createParser(text)) {
            while (parser.nextToken() != null) {
                // Keep parsing to surface the first syntax error, if any.
            }
        } catch (JsonParseException e) {
            var offset = toOffset(text, e);
            if (isInsideTemplate(text, offset)) {
                return;
            }
            holder.newAnnotation(HighlightSeverity.ERROR, e.getOriginalMessage())
                    .range(TextRange.from(offset, 1))
                    .create();
        } catch (Throwable ignored) {
            // Do not block editing if JSON parser is unavailable in IDE runtime.
        }
    }

    private static boolean isInsideTemplate(String text, int offset) {
        if (text.isEmpty()) {
            return false;
        }
        var target = Math.min(Math.max(offset, 0), text.length() - 1);
        var inTemplate = false;
        for (int i = 0; i < text.length(); i++) {
            if (i == target) {
                return inTemplate;
            }
            if (inTemplate) {
                if (text.charAt(i) == '}' && i + 1 < text.length() && text.charAt(i + 1) == '}') {
                    inTemplate = false;
                    i++;
                }
                continue;
            }
            if (isTemplateStart(text, i)) {
                inTemplate = true;
            }
        }
        return false;
    }

    private static void validateSubstitutions(List<Token> tokens, AnnotationHolder holder) {
        validateSubstitutions(tokens, 0, holder);
    }

    private static void validateSubstitutions(List<Token> tokens, int baseOffset, AnnotationHolder holder) {
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.type != TokenType.PIPE) {
                continue;
            }
            var next = findNextNonTextToken(tokens, i + 1);
            if (next == null || !isValidTokenAfterPipe(next.type())) {
                holder.newAnnotation(HighlightSeverity.ERROR, "Missing expression after pipe operator")
                        .range(TextRange.create(baseOffset + token.start, baseOffset + Math.max(token.end, token.start + 1)))
                        .create();
            }
        }
    }

    private static boolean isValidTokenAfterPipe(TokenType type) {
        return switch (type) {
            case IDENT, STRING, NUMBER, BOOLEAN, NULL, LPAREN, DOT, KEYWORD -> true;
            default -> false;
        };
    }

    private static boolean isDefinitionName(List<Token> tokens, int identIndex) {
        var next = findNextNonTextToken(tokens, identIndex + 1);
        if (next == null || next.type() != TokenType.KEYWORD) {
            return false;
        }
        return Keyword.SWITCH.eq(next.token().lexeme) || Keyword.RANGE.eq(next.token().lexeme);
    }

    private static boolean isRangeBindingName(List<Token> tokens, int identIndex) {
        var previous = findPreviousNonTextToken(tokens, identIndex - 1);
        if (previous == null) {
            return false;
        }
        if (previous.type() == TokenType.KEYWORD && Keyword.RANGE.eq(previous.token().lexeme)) {
            return true;
        }
        if (previous.type() != TokenType.COMMA) {
            return false;
        }
        var beforeComma = findPreviousNonTextToken(tokens, previous.index() - 1);
        if (beforeComma == null || beforeComma.type() != TokenType.IDENT) {
            return false;
        }
        var beforeFirstBinding = findPreviousNonTextToken(tokens, beforeComma.index() - 1);
        return beforeFirstBinding != null
                && beforeFirstBinding.type() == TokenType.KEYWORD
                && Keyword.RANGE.eq(beforeFirstBinding.token().lexeme);
    }

    private static boolean isFunctionCall(List<Token> tokens, int identIndex) {
        var previous = findPreviousNonTextToken(tokens, identIndex - 1);
        if (previous != null && previous.type() == TokenType.PIPE) {
            return true;
        }
        var next = findNextNonTextToken(tokens, identIndex + 1);
        if (next != null && next.type() == TokenType.LPAREN) {
            return true;
        }
        if (next != null && next.type() == TokenType.COLON) {
            var nextNext = findNextNonTextToken(tokens, next.index() + 1);
            return nextNext != null && nextNext.type() == TokenType.COLON;
        }
        if (previous != null && previous.type() == TokenType.COLON) {
            var previousPrevious = findPreviousNonTextToken(tokens, previous.index() - 1);
            if (previousPrevious != null && previousPrevious.type() == TokenType.COLON) {
                return isPipedIdentifier(tokens, previousPrevious.index() - 1);
            }
            if (previousPrevious != null && previousPrevious.type() == TokenType.IDENT) {
                return isPipedIdentifier(tokens, previousPrevious.index());
            }
        }
        return isNamespacedFunctionPrefix(tokens, identIndex);
    }

    private static boolean isNamespacedFunctionPrefix(List<Token> tokens, int identIndex) {
        var next = findNextNonTextToken(tokens, identIndex + 1);
        if (next == null || next.type() != TokenType.COLON) {
            return false;
        }
        var afterFirstColon = findNextNonTextToken(tokens, next.index() + 1);
        if (afterFirstColon == null) {
            return false;
        }
        if (afterFirstColon.type() == TokenType.IDENT) {
            return isPipedIdentifier(tokens, identIndex);
        }
        if (afterFirstColon.type() != TokenType.COLON) {
            return false;
        }
        var functionName = findNextNonTextToken(tokens, afterFirstColon.index() + 1);
        return functionName != null
                && functionName.type() == TokenType.IDENT
                && isPipedIdentifier(tokens, identIndex);
    }

    private static boolean isPipedIdentifier(List<Token> tokens, int identIndex) {
        if (identIndex < 0 || identIndex >= tokens.size()) {
            return false;
        }
        if (tokens.get(identIndex).type != TokenType.IDENT) {
            return false;
        }
        var previous = findPreviousNonTextToken(tokens, identIndex - 1);
        return previous != null && previous.type() == TokenType.PIPE;
    }

    private static boolean isContextVariable(List<Token> tokens,
                                             int identIndex,
                                             Set<String> localDefinitions,
                                             Set<String> rangeBindings) {
        var token = tokens.get(identIndex);
        if (!isRootVariable(tokens, identIndex)) {
            return false;
        }
        if (localDefinitions.contains(token.lexeme)) {
            return false;
        }
        return !rangeBindings.contains(token.lexeme);
    }

    private static boolean isRootVariable(List<Token> tokens, int identIndex) {
        var previous = findPreviousNonTextToken(tokens, identIndex - 1);
        if (previous == null || previous.type() != TokenType.DOT) {
            return false;
        }
        var beforeDot = findPreviousNonTextToken(tokens, previous.index() - 1);
        return beforeDot == null || beforeDot.type() != TokenType.IDENT;
    }

    private static Set<String> collectRangeBindings(List<Token> tokens) {
        var bindings = new HashSet<String>();
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.type != TokenType.KEYWORD || !Keyword.RANGE.eq(token.lexeme)) {
                continue;
            }
            var firstBinding = findNextNonTextToken(tokens, i + 1);
            if (firstBinding == null || firstBinding.type() != TokenType.IDENT) {
                continue;
            }
            bindings.add(firstBinding.token().lexeme);

            var maybeComma = findNextNonTextToken(tokens, firstBinding.index() + 1);
            if (maybeComma == null || maybeComma.type() != TokenType.COMMA) {
                continue;
            }
            var secondBinding = findNextNonTextToken(tokens, maybeComma.index() + 1);
            if (secondBinding != null && secondBinding.type() == TokenType.IDENT) {
                bindings.add(secondBinding.token().lexeme);
            }
        }
        return bindings;
    }

    private static Set<String> extractLocalDefinitions(String source) {
        try {
            var script = MAPPER.readTree(source);
            var definitionsNode = script.get("definitions");
            if (definitionsNode == null || !definitionsNode.isArray()) {
                return Set.of();
            }
            var result = new HashSet<String>();
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
                var next = findNextNonTextToken(tokens, i + 1);
                if (next == null || next.type() != TokenType.KEYWORD) {
                    continue;
                }
                if (Keyword.RANGE.eq(next.token().lexeme) || Keyword.SWITCH.eq(next.token().lexeme)) {
                    names.add(token.lexeme);
                }
            }
        } catch (Exception ignored) {
            return Set.of();
        }
        return names;
    }

    private static IndexedToken findPreviousNonTextToken(List<Token> tokens, int from) {
        for (int i = from; i >= 0; i--) {
            if (tokens.get(i).type != TokenType.TEXT) {
                return new IndexedToken(i, tokens.get(i));
            }
        }
        return null;
    }

    private static IndexedToken findNextNonTextToken(List<Token> tokens, int from) {
        for (int i = from; i < tokens.size(); i++) {
            if (tokens.get(i).type != TokenType.TEXT) {
                return new IndexedToken(i, tokens.get(i));
            }
        }
        return null;
    }

    private static int toOffset(String text, JsonParseException error) {
        var location = error.getLocation();
        if (location != null && location.getCharOffset() >= 0) {
            var offset = (int) location.getCharOffset();
            return Math.min(Math.max(offset, 0), Math.max(text.length() - 1, 0));
        }
        if (location == null || location.getLineNr() <= 0 || location.getColumnNr() <= 0) {
            return 0;
        }
        var line = 1;
        var index = 0;
        while (index < text.length() && line < location.getLineNr()) {
            if (text.charAt(index) == '\n') {
                line++;
            }
            index++;
        }
        var offset = index + location.getColumnNr() - 1;
        return Math.min(Math.max(offset, 0), Math.max(text.length() - 1, 0));
    }

    private static void annotateRange(AnnotationHolder holder,
                                      int start,
                                      int endExclusive,
                                      TextAttributesKey key) {
        if (start >= endExclusive) {
            return;
        }
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange.create(start, endExclusive))
                .textAttributes(key)
                .create();
    }

    private static int readWordEnd(String text, int from) {
        var index = from;
        while (index < text.length() && Character.isLetter(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int readNumberEnd(String text, int from) {
        var index = from;
        if (text.charAt(index) == '-') {
            index++;
        }
        var intStart = index;
        while (index < text.length() && Character.isDigit(text.charAt(index))) {
            index++;
        }
        if (intStart == index) {
            return from;
        }
        if (index < text.length() && text.charAt(index) == '.') {
            var dot = index++;
            var fractionStart = index;
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            if (fractionStart == index) {
                return dot;
            }
        }
        if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
            var expStart = index++;
            if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                index++;
            }
            var digitsStart = index;
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            if (digitsStart == index) {
                return expStart;
            }
        }
        return index;
    }

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof JjtemplateFile)) {
            return;
        }
        var text = element.getText();
        var localDefinitions = extractLocalDefinitions(text);
        highlightJsonLike(text, holder);
        validateJson(text, holder);
        try {
            var tokens = new TemplateLexer(text).tokens();
            var rangeBindings = collectRangeBindings(tokens);
            validateSubstitutions(tokens, holder);
            highlightTemplateIdentifiers(tokens, holder, localDefinitions, rangeBindings);
        } catch (TemplateLexerException e) {
            var position = Math.min(Math.max(e.getPosition(), 0), Math.max(text.length() - 1, 0));
            holder.newAnnotation(HighlightSeverity.ERROR, e.getMessage())
                    .range(TextRange.from(position, 1))
                    .create();
        } catch (Throwable t) {
            if (text.isEmpty()) {
                return;
            }
            holder.newAnnotation(HighlightSeverity.WARNING, "JJTemplate lexer is unavailable: " + t.getClass().getSimpleName())
                    .range(TextRange.from(0, 1))
                    .create();
        }
    }

    private record IndexedToken(int index, Token token) {
        private TokenType type() {
            return token.type;
        }
    }

    private static void highlightTemplateIdentifiers(List<Token> tokens,
                                                     AnnotationHolder holder,
                                                     Set<String> localDefinitions,
                                                     Set<String> rangeBindings) {
        highlightTemplateIdentifiers(tokens, 0, holder, localDefinitions, rangeBindings);
    }

    private static void highlightTemplateIdentifiers(List<Token> tokens,
                                                     int baseOffset,
                                                     AnnotationHolder holder,
                                                     Set<String> localDefinitions,
                                                     Set<String> rangeBindings) {
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.type != TokenType.IDENT) {
                continue;
            }
            var key = isDefinitionName(tokens, i) || isRangeBindingName(tokens, i)
                    ? JjtemplateSyntaxHighlighter.OBJECT_KEY
                    : isFunctionCall(tokens, i)
                    ? JjtemplateSyntaxHighlighter.TEMPLATE_FUNCTION
                    : isContextVariable(tokens, i, localDefinitions, rangeBindings)
                    ? JjtemplateSyntaxHighlighter.TEMPLATE_CONTEXT_VARIABLE
                    : JjtemplateSyntaxHighlighter.TEMPLATE_VARIABLE;
            annotateRange(holder, baseOffset + token.start, baseOffset + token.end, key);
        }
    }
}
