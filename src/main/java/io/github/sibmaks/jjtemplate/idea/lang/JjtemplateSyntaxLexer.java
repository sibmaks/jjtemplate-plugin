package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import io.github.sibmaks.jjtemplate.lexer.TemplateLexer;
import io.github.sibmaks.jjtemplate.lexer.api.Token;
import io.github.sibmaks.jjtemplate.lexer.api.TokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class JjtemplateSyntaxLexer extends LexerBase {
    private CharSequence buffer;
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
            this.tokens = new TemplateLexer(source).tokens();
        } catch (Exception e) {
            this.tokens = List.of(new Token(TokenType.TEXT, source, 0, source.length()));
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
        return startOffset + tokens.get(index).end;
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
}
