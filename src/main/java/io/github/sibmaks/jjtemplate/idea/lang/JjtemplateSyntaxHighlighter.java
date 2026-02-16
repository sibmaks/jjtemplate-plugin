package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class JjtemplateSyntaxHighlighter extends SyntaxHighlighterBase {
    private static final TextAttributesKey[] BAD_CHAR = pack(HighlighterColors.BAD_CHARACTER);
    private static final TextAttributesKey[] EMPTY = TextAttributesKey.EMPTY_ARRAY;

    private static final Map<IElementType, TextAttributesKey[]> ATTRIBUTES = Map.ofEntries(
            Map.entry(JjtemplateTokenTypes.OPEN_EXPR, pack(DefaultLanguageHighlighterColors.BRACES)),
            Map.entry(JjtemplateTokenTypes.OPEN_COND, pack(DefaultLanguageHighlighterColors.BRACES)),
            Map.entry(JjtemplateTokenTypes.OPEN_SPREAD, pack(DefaultLanguageHighlighterColors.BRACES)),
            Map.entry(JjtemplateTokenTypes.CLOSE, pack(DefaultLanguageHighlighterColors.BRACES)),
            Map.entry(JjtemplateTokenTypes.KEYWORD, pack(DefaultLanguageHighlighterColors.KEYWORD)),
            Map.entry(JjtemplateTokenTypes.STRING, pack(DefaultLanguageHighlighterColors.STRING)),
            Map.entry(JjtemplateTokenTypes.NUMBER, pack(DefaultLanguageHighlighterColors.NUMBER)),
            Map.entry(JjtemplateTokenTypes.BOOLEAN, pack(DefaultLanguageHighlighterColors.KEYWORD)),
            Map.entry(JjtemplateTokenTypes.NULL, pack(DefaultLanguageHighlighterColors.KEYWORD)),
            Map.entry(JjtemplateTokenTypes.PIPE, pack(DefaultLanguageHighlighterColors.OPERATION_SIGN)),
            Map.entry(JjtemplateTokenTypes.DOT, pack(DefaultLanguageHighlighterColors.DOT)),
            Map.entry(JjtemplateTokenTypes.COMMA, pack(DefaultLanguageHighlighterColors.COMMA)),
            Map.entry(JjtemplateTokenTypes.COLON, pack(DefaultLanguageHighlighterColors.OPERATION_SIGN)),
            Map.entry(JjtemplateTokenTypes.QUESTION, pack(DefaultLanguageHighlighterColors.OPERATION_SIGN)),
            Map.entry(JjtemplateTokenTypes.LPAREN, pack(DefaultLanguageHighlighterColors.PARENTHESES)),
            Map.entry(JjtemplateTokenTypes.RPAREN, pack(DefaultLanguageHighlighterColors.PARENTHESES)),
            Map.entry(JjtemplateTokenTypes.IDENT, pack(DefaultLanguageHighlighterColors.IDENTIFIER))
    );

    @Override
    public @NotNull Lexer getHighlightingLexer() {
        return new JjtemplateSyntaxLexer();
    }

    @Override
    public @NotNull TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        if (tokenType == null) {
            return EMPTY;
        }
        if (tokenType == com.intellij.psi.TokenType.BAD_CHARACTER) {
            return BAD_CHAR;
        }
        return ATTRIBUTES.getOrDefault(tokenType, EMPTY);
    }
}
