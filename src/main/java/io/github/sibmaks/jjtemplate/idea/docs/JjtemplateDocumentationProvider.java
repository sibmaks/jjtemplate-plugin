package io.github.sibmaks.jjtemplate.idea.docs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.psi.PsiElement;
import io.github.sibmaks.jjtemplate.idea.lang.JjtemplateGotoDeclarationHandler;
import io.github.sibmaks.jjtemplate.idea.lang.JjtemplateLanguage;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public final class JjtemplateDocumentationProvider extends AbstractDocumentationProvider {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public @Nullable @Nls String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        if (element instanceof JjtemplateGotoDeclarationHandler.OffsetNavigationElement navigationElement) {
            return "<h2>" + escapeHtml(navigationElement.getName()) + "</h2>"
                    + "<p><b>Kind:</b> JJTemplate local variable</p>"
                    + formatPreviewValue(navigationElement.getPreviewText());
        }
        if (element == null || !element.getLanguage().isKindOf(JjtemplateLanguage.INSTANCE)) {
            return null;
        }
        var key = resolveLookupKey(element, originalElement);
        if (key == null || key.isBlank()) {
            return null;
        }

        var function = BuiltInFunctionIndex.find(key);
        if (function == null) {
            return null;
        }

        return "<h2>" + function.presentableName() + "</h2>"
                + "<p>" + function.description() + "</p>"
                + "<p><b>Kind:</b> JJTemplate built-in function</p>";
    }

    private static String resolveLookupKey(PsiElement element, @Nullable PsiElement originalElement) {
        var sourceElement = originalElement != null ? originalElement : element;
        var file = sourceElement.getContainingFile();
        if (file == null) {
            return element.getText();
        }
        var text = file.getText();
        if (text == null || text.isEmpty()) {
            return element.getText();
        }
        var offset = Math.min(Math.max(sourceElement.getTextOffset(), 0), text.length() - 1);
        if (!isFunctionTokenChar(text.charAt(offset)) && offset > 0 && isFunctionTokenChar(text.charAt(offset - 1))) {
            offset--;
        }
        if (!isFunctionTokenChar(text.charAt(offset))) {
            return element.getText();
        }

        var start = offset;
        while (start > 0 && isFunctionTokenChar(text.charAt(start - 1))) {
            start--;
        }
        var end = offset + 1;
        while (end < text.length() && isFunctionTokenChar(text.charAt(end))) {
            end++;
        }
        var token = text.substring(start, end);
        if (token.contains("::")) {
            return token;
        }
        return element.getText();
    }

    private static boolean isFunctionTokenChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == ':';
    }

    private static String formatPreviewValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "<p><b>Value:</b> <code></code></p>";
        }
        try {
            var node = OBJECT_MAPPER.readTree(rawValue);
            if (node.isTextual()) {
                return "<p><b>Value:</b> <code>" + escapeHtml(node.textValue()) + "</code></p>";
            }
            if (node.isNumber() || node.isBoolean() || node.isNull()) {
                return "<p><b>Value:</b> <code>" + escapeHtml(node.toString()) + "</code></p>";
            }
            if (node.isArray() || node.isObject()) {
                var pretty = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
                return "<p><b>Value:</b></p><pre><code>" + escapeHtml(pretty) + "</code></pre>";
            }
            return "<p><b>Value:</b> <code>" + escapeHtml(node.toString()) + "</code></p>";
        } catch (Throwable ignored) {
            return "<p><b>Value:</b></p><pre><code>" + escapeHtml(rawValue) + "</code></pre>";
        }
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
