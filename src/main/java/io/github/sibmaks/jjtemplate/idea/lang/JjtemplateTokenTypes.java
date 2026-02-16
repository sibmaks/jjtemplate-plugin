package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.psi.tree.IElementType;

public final class JjtemplateTokenTypes {
    public static final IElementType TEXT = new IElementType("TEXT", JjtemplateLanguage.INSTANCE);
    public static final IElementType OPEN_EXPR = new IElementType("OPEN_EXPR", JjtemplateLanguage.INSTANCE);
    public static final IElementType OPEN_COND = new IElementType("OPEN_COND", JjtemplateLanguage.INSTANCE);
    public static final IElementType OPEN_SPREAD = new IElementType("OPEN_SPREAD", JjtemplateLanguage.INSTANCE);
    public static final IElementType CLOSE = new IElementType("CLOSE", JjtemplateLanguage.INSTANCE);
    public static final IElementType PIPE = new IElementType("PIPE", JjtemplateLanguage.INSTANCE);
    public static final IElementType DOT = new IElementType("DOT", JjtemplateLanguage.INSTANCE);
    public static final IElementType COMMA = new IElementType("COMMA", JjtemplateLanguage.INSTANCE);
    public static final IElementType COLON = new IElementType("COLON", JjtemplateLanguage.INSTANCE);
    public static final IElementType QUESTION = new IElementType("QUESTION", JjtemplateLanguage.INSTANCE);
    public static final IElementType LPAREN = new IElementType("LPAREN", JjtemplateLanguage.INSTANCE);
    public static final IElementType RPAREN = new IElementType("RPAREN", JjtemplateLanguage.INSTANCE);
    public static final IElementType STRING = new IElementType("STRING", JjtemplateLanguage.INSTANCE);
    public static final IElementType NUMBER = new IElementType("NUMBER", JjtemplateLanguage.INSTANCE);
    public static final IElementType BOOLEAN = new IElementType("BOOLEAN", JjtemplateLanguage.INSTANCE);
    public static final IElementType NULL = new IElementType("NULL", JjtemplateLanguage.INSTANCE);
    public static final IElementType IDENT = new IElementType("IDENT", JjtemplateLanguage.INSTANCE);
    public static final IElementType KEYWORD = new IElementType("KEYWORD", JjtemplateLanguage.INSTANCE);

    private JjtemplateTokenTypes() {
    }
}
