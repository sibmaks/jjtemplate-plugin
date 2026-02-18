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
    public static final TextAttributesKey JSON_STRING =
            TextAttributesKey.createTextAttributesKey("JJTEMPLATE.JSON_STRING", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey JSON_NUMBER =
            TextAttributesKey.createTextAttributesKey("JJTEMPLATE.JSON_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
    public static final TextAttributesKey JSON_BOOLEAN =
            TextAttributesKey.createTextAttributesKey("JJTEMPLATE.JSON_BOOLEAN", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey OBJECT_KEY =
            TextAttributesKey.createTextAttributesKey("JJTEMPLATE.OBJECT_KEY", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    public static final TextAttributesKey ROOT_OBJECT =
            TextAttributesKey.createTextAttributesKey("JJTEMPLATE.ROOT_OBJECT", DefaultLanguageHighlighterColors.BRACES);
    public static final TextAttributesKey ROOT_ARRAY =
            TextAttributesKey.createTextAttributesKey("JJTEMPLATE.ROOT_ARRAY", DefaultLanguageHighlighterColors.BRACKETS);
    public static final TextAttributesKey TEMPLATE_FUNCTION =
            TextAttributesKey.createTextAttributesKey("JJTEMPLATE.TEMPLATE_FUNCTION", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);
    public static final TextAttributesKey TEMPLATE_VARIABLE =
            TextAttributesKey.createTextAttributesKey("JJTEMPLATE.TEMPLATE_VARIABLE", DefaultLanguageHighlighterColors.LOCAL_VARIABLE);
    public static final TextAttributesKey TEMPLATE_CONTEXT_VARIABLE =
            TextAttributesKey.createTextAttributesKey("JJTEMPLATE.TEMPLATE_CONTEXT_VARIABLE", DefaultLanguageHighlighterColors.METADATA);

    private static final TextAttributesKey[] BAD_CHAR = pack(HighlighterColors.BAD_CHARACTER);
    private static final TextAttributesKey[] EMPTY = TextAttributesKey.EMPTY_ARRAY;

    private static final Map<IElementType, TextAttributesKey[]> ATTRIBUTES = Map.ofEntries(
            Map.entry(JjtemplateTokenTypes.OPEN_EXPR, pack(DefaultLanguageHighlighterColors.BRACES)),
            Map.entry(JjtemplateTokenTypes.OPEN_COND, pack(DefaultLanguageHighlighterColors.BRACES)),
            Map.entry(JjtemplateTokenTypes.OPEN_SPREAD, pack(DefaultLanguageHighlighterColors.BRACES)),
            Map.entry(JjtemplateTokenTypes.CLOSE, pack(DefaultLanguageHighlighterColors.BRACES)),
            Map.entry(JjtemplateTokenTypes.KEYWORD, pack(DefaultLanguageHighlighterColors.KEYWORD)),
            Map.entry(JjtemplateTokenTypes.STRING, pack(JSON_STRING)),
            Map.entry(JjtemplateTokenTypes.NUMBER, pack(JSON_NUMBER)),
            Map.entry(JjtemplateTokenTypes.BOOLEAN, pack(JSON_BOOLEAN)),
            Map.entry(JjtemplateTokenTypes.NULL, pack(JSON_BOOLEAN)),
            Map.entry(JjtemplateTokenTypes.PIPE, pack(DefaultLanguageHighlighterColors.OPERATION_SIGN)),
            Map.entry(JjtemplateTokenTypes.DOT, pack(DefaultLanguageHighlighterColors.DOT)),
            Map.entry(JjtemplateTokenTypes.COMMA, pack(DefaultLanguageHighlighterColors.COMMA)),
            Map.entry(JjtemplateTokenTypes.COLON, pack(DefaultLanguageHighlighterColors.OPERATION_SIGN)),
            Map.entry(JjtemplateTokenTypes.QUESTION, pack(DefaultLanguageHighlighterColors.OPERATION_SIGN)),
            Map.entry(JjtemplateTokenTypes.LPAREN, pack(DefaultLanguageHighlighterColors.PARENTHESES)),
            Map.entry(JjtemplateTokenTypes.RPAREN, pack(DefaultLanguageHighlighterColors.PARENTHESES)),
            Map.entry(JjtemplateTokenTypes.LBRACE, pack(DefaultLanguageHighlighterColors.BRACES)),
            Map.entry(JjtemplateTokenTypes.RBRACE, pack(DefaultLanguageHighlighterColors.BRACES)),
            Map.entry(JjtemplateTokenTypes.LBRACKET, pack(DefaultLanguageHighlighterColors.BRACKETS)),
            Map.entry(JjtemplateTokenTypes.RBRACKET, pack(DefaultLanguageHighlighterColors.BRACKETS)),
            Map.entry(JjtemplateTokenTypes.IDENT, pack(TEMPLATE_VARIABLE))
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
