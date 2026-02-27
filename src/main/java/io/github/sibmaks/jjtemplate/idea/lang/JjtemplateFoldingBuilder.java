package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class JjtemplateFoldingBuilder extends FoldingBuilderEx {
    @Override
    public @NotNull FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root,
                                                                    @NotNull Document document,
                                                                    boolean quick) {
        var text = document.getCharsSequence();
        var stack = new ArrayDeque<BlockStart>();
        var result = new ArrayList<FoldingDescriptor>();
        var inString = false;
        var escaped = false;

        for (int i = 0; i < text.length(); i++) {
            var ch = text.charAt(i);
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
                }
                continue;
            }
            if (TemplateTextScanner.isTemplateStart(text, i)) {
                var templateEnd = TemplateTextScanner.findTemplateEnd(text, i, text.length());
                if (templateEnd < 0) {
                    break;
                }
                i = templateEnd - 1;
                continue;
            }
            if (ch == '"') {
                inString = true;
                continue;
            }
            if (ch == '{' || ch == '[') {
                stack.push(new BlockStart(ch, i));
                continue;
            }
            if (ch == '}' || ch == ']') {
                var expectedOpen = ch == '}' ? '{' : '[';
                if (stack.isEmpty()) {
                    continue;
                }
                var open = stack.pop();
                if (open.ch != expectedOpen) {
                    continue;
                }
                if (open.offset + 1 >= i) {
                    continue;
                }
                if (!containsLineBreak(text, open.offset + 1, i)) {
                    continue;
                }
                result.add(new FoldingDescriptor(root.getNode(), TextRange.create(open.offset + 1, i)));
            }
        }

        return result.toArray(FoldingDescriptor.EMPTY);
    }

    @Override
    public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
        return "...";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return false;
    }

    private static boolean containsLineBreak(CharSequence text, int fromInclusive, int toExclusive) {
        for (int i = fromInclusive; i < toExclusive; i++) {
            if (text.charAt(i) == '\n' || text.charAt(i) == '\r') {
                return true;
            }
        }
        return false;
    }

    private record BlockStart(char ch, int offset) {
    }
}
