package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import io.github.sibmaks.jjtemplate.lexer.TemplateLexer;
import io.github.sibmaks.jjtemplate.lexer.api.TokenType;
import io.github.sibmaks.jjtemplate.lexer.api.TemplateLexerException;
import org.jetbrains.annotations.NotNull;

public final class JjtemplateAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof JjtemplateFile)) {
            return;
        }
        var text = element.getText();
        highlightJsonLike(text, holder);
        try {
            new TemplateLexer(text).tokens();
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
                                                   com.intellij.openapi.editor.colors.TextAttributesKey plainTextStyle) {
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

    private static com.intellij.openapi.editor.colors.TextAttributesKey mapLookupToken(TokenType tokenType) {
        return switch (tokenType) {
            case OPEN_EXPR, OPEN_COND, OPEN_SPREAD, CLOSE -> DefaultLanguageHighlighterColors.BRACES;
            case KEYWORD, BOOLEAN, NULL -> DefaultLanguageHighlighterColors.KEYWORD;
            case STRING -> DefaultLanguageHighlighterColors.STRING;
            case NUMBER -> DefaultLanguageHighlighterColors.NUMBER;
            case PIPE, COLON, QUESTION -> DefaultLanguageHighlighterColors.OPERATION_SIGN;
            case DOT -> DefaultLanguageHighlighterColors.DOT;
            case COMMA -> DefaultLanguageHighlighterColors.COMMA;
            case LPAREN, RPAREN -> DefaultLanguageHighlighterColors.PARENTHESES;
            case IDENT -> DefaultLanguageHighlighterColors.IDENTIFIER;
            case TEXT -> null;
        };
    }

    private static void annotateRange(AnnotationHolder holder,
                                      int start,
                                      int endExclusive,
                                      com.intellij.openapi.editor.colors.TextAttributesKey key) {
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
}
