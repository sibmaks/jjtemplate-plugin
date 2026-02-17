package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JjtemplateBraceMatcher implements PairedBraceMatcher {
    private static final BracePair[] PAIRS = {
            new BracePair(JjtemplateTokenTypes.LBRACE, JjtemplateTokenTypes.RBRACE, true),
            new BracePair(JjtemplateTokenTypes.LBRACKET, JjtemplateTokenTypes.RBRACKET, true),
            new BracePair(JjtemplateTokenTypes.LPAREN, JjtemplateTokenTypes.RPAREN, false)
    };

    @Override
    public BracePair @NotNull [] getPairs() {
        return PAIRS;
    }

    @Override
    public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
        return true;
    }

    @Override
    public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
        return openingBraceOffset;
    }
}
