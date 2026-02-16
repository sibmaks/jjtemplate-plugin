package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import io.github.sibmaks.jjtemplate.lexer.TemplateLexer;
import io.github.sibmaks.jjtemplate.lexer.api.Token;
import io.github.sibmaks.jjtemplate.lexer.api.TokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

public final class JjtemplateSyntaxLexer extends LexerBase {
    private CharSequence buffer = "";
    private int startOffset;
    private int endOffset;
    private List<Token> tokens = Collections.emptyList();
    private int index;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.index = 0;
        var source = buffer.subSequence(startOffset, endOffset).toString();
        try {
            this.tokens = normalizeTokens(new TemplateLexer(source).tokens(), source);
        } catch (Throwable e) {
            this.tokens = fallbackTokens(source);
        }
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public @Nullable IElementType getTokenType() {
        if (index >= tokens.size()) {
            return null;
        }
        return mapType(tokens.get(index).type);
    }

    @Override
    public int getTokenStart() {
        if (index >= tokens.size()) {
            return endOffset;
        }
        return startOffset + tokens.get(index).start;
    }

    @Override
    public int getTokenEnd() {
        if (index >= tokens.size()) {
            return endOffset;
        }
        var token = tokens.get(index);
        var tokenStart = startOffset + token.start;
        var tokenEnd = startOffset + token.end;
        if (tokenEnd <= tokenStart) {
            return Math.min(endOffset, tokenStart + 1);
        }
        return Math.min(endOffset, tokenEnd);
    }

    @Override
    public void advance() {
        if (index < tokens.size()) {
            index++;
        }
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return endOffset;
    }

    private static IElementType mapType(TokenType tokenType) {
        return switch (tokenType) {
            case TEXT -> JjtemplateTokenTypes.TEXT;
            case OPEN_EXPR -> JjtemplateTokenTypes.OPEN_EXPR;
            case OPEN_COND -> JjtemplateTokenTypes.OPEN_COND;
            case OPEN_SPREAD -> JjtemplateTokenTypes.OPEN_SPREAD;
            case CLOSE -> JjtemplateTokenTypes.CLOSE;
            case PIPE -> JjtemplateTokenTypes.PIPE;
            case DOT -> JjtemplateTokenTypes.DOT;
            case COMMA -> JjtemplateTokenTypes.COMMA;
            case COLON -> JjtemplateTokenTypes.COLON;
            case QUESTION -> JjtemplateTokenTypes.QUESTION;
            case LPAREN -> JjtemplateTokenTypes.LPAREN;
            case RPAREN -> JjtemplateTokenTypes.RPAREN;
            case STRING -> JjtemplateTokenTypes.STRING;
            case NUMBER -> JjtemplateTokenTypes.NUMBER;
            case BOOLEAN -> JjtemplateTokenTypes.BOOLEAN;
            case NULL -> JjtemplateTokenTypes.NULL;
            case IDENT -> JjtemplateTokenTypes.IDENT;
            case KEYWORD -> JjtemplateTokenTypes.KEYWORD;
        };
    }

    private static List<Token> normalizeTokens(List<Token> sourceTokens, String source) {
        if (source.isEmpty()) {
            return Collections.emptyList();
        }
        var length = source.length();
        var normalized = new ArrayList<Token>(sourceTokens.size());
        var currentStart = 0;
        for (var token : sourceTokens) {
            var start = Math.max(token.start, 0);
            var end = Math.min(token.end, length);
            if (start > currentStart) {
                normalized.add(new Token(TokenType.TEXT, source.substring(currentStart, start), currentStart, start));
                currentStart = start;
            }
            if (start < currentStart) {
                start = currentStart;
            }
            if (end <= start) {
                continue;
            }
            normalized.add(new Token(token.type, source.substring(start, end), start, end));
            currentStart = end;
            if (currentStart >= length) {
                break;
            }
        }
        if (currentStart < length) {
            normalized.add(new Token(TokenType.TEXT, source.substring(currentStart), currentStart, length));
        }
        if (normalized.isEmpty()) {
            return fallbackTokens(source);
        }
        return normalized;
    }

    private static List<Token> fallbackTokens(String source) {
        if (source.isEmpty()) {
            return Collections.emptyList();
        }
        return List.of(new Token(TokenType.TEXT, source, 0, source.length()));
    }
}
