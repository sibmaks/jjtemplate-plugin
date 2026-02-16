package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class JjtemplateFileType extends LanguageFileType {
    public static final JjtemplateFileType INSTANCE = new JjtemplateFileType();
    private static final Icon ICON = IconLoader.getIcon("/icons/jjtemplate.svg", JjtemplateFileType.class);

    private JjtemplateFileType() {
        super(JjtemplateLanguage.INSTANCE);
    }

    @Override
    public @NotNull String getName() {
        return "JJTemplate";
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
        return ICON;
    }
}
