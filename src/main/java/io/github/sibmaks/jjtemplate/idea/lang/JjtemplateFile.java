package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public final class JjtemplateFile extends PsiFileBase {
    public JjtemplateFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, JjtemplateLanguage.INSTANCE);
    }

    @Override
    public @NotNull FileType getFileType() {
        return JjtemplateFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "JJTemplate File";
    }
}
