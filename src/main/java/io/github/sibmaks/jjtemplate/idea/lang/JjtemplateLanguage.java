package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.lang.Language;

public final class JjtemplateLanguage extends Language {
    public static final JjtemplateLanguage INSTANCE = new JjtemplateLanguage();

    private JjtemplateLanguage() {
        super("JJTemplate");
    }
}
