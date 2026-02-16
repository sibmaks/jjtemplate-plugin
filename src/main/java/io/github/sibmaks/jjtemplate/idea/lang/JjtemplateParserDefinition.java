package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerBase;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JjtemplateParserDefinition implements ParserDefinition {
    private static final IFileElementType FILE = new IFileElementType(JjtemplateLanguage.INSTANCE);

    @Override
    public @NotNull Lexer createLexer(Project project) {
        return new WholeFileLexer();
    }

    @Override
    public @NotNull PsiParser createParser(Project project) {
        return (root, builder) -> {
            PsiBuilder.Marker marker = builder.mark();
            while (!builder.eof()) {
                builder.advanceLexer();
            }
            marker.done(root);
            return builder.getTreeBuilt();
        };
    }

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return FILE;
    }

    @Override
    public @NotNull TokenSet getCommentTokens() {
        return TokenSet.EMPTY;
    }

    @Override
    public @NotNull TokenSet getStringLiteralElements() {
        return TokenSet.EMPTY;
    }

    @Override
    public @NotNull PsiElement createElement(ASTNode node) {
        return new ASTWrapperPsiElement(node);
    }

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new JjtemplateFile(viewProvider);
    }

    private static final class WholeFileLexer extends LexerBase {
        private CharSequence buffer = "";
        private int start;
        private int end;
        private boolean done;

        @Override
        public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
            this.buffer = buffer;
            this.start = startOffset;
            this.end = endOffset;
            this.done = startOffset >= endOffset;
        }

        @Override
        public int getState() {
            return 0;
        }

        @Override
        public @Nullable com.intellij.psi.tree.IElementType getTokenType() {
            return done ? null : JjtemplateTokenTypes.TEXT;
        }

        @Override
        public int getTokenStart() {
            return start;
        }

        @Override
        public int getTokenEnd() {
            return done ? end : end;
        }

        @Override
        public void advance() {
            done = true;
        }

        @Override
        public @NotNull CharSequence getBufferSequence() {
            return buffer;
        }

        @Override
        public int getBufferEnd() {
            return end;
        }
    }
}
