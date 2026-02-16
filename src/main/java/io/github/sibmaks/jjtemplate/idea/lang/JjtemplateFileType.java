package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class JjtemplateFileType extends LanguageFileType {
    public static final JjtemplateFileType INSTANCE = new JjtemplateFileType();

    private JjtemplateFileType() {
        super(JjtemplateLanguage.INSTANCE);
    }

    @Override
    public @NotNull String getName() {
        return "JJTemplate file";
    }

    @Override
    public @NotNull String getDescription() {
        return "JJTemplate template";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "jjtemplate";
    }

    @Override
    public @Nullable Icon getIcon() {
        return null;
    }
}
