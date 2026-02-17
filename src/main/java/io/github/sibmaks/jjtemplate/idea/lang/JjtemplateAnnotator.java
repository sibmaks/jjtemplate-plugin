package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import io.github.sibmaks.jjtemplate.lexer.TemplateLexer;
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
                        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                                .range(TextRange.create(stringStart, i + 1))
                                .textAttributes(JjtemplateSyntaxHighlighter.OBJECT_KEY)
                                .create();
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
}
