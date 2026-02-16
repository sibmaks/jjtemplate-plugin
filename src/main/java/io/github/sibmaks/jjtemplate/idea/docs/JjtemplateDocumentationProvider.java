package io.github.sibmaks.jjtemplate.idea.docs;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.psi.PsiElement;
import io.github.sibmaks.jjtemplate.idea.lang.JjtemplateLanguage;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public final class JjtemplateDocumentationProvider extends AbstractDocumentationProvider {
    @Override
    public @Nullable @Nls String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        if (element == null || !element.getLanguage().isKindOf(JjtemplateLanguage.INSTANCE)) {
            return null;
        }
        var key = element.getText();
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
}
