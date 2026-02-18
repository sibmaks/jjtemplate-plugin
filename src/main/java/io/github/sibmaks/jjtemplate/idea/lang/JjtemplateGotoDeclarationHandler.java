package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.FakePsiElement;
import io.github.sibmaks.jjtemplate.lexer.TemplateLexer;
import io.github.sibmaks.jjtemplate.lexer.api.Keyword;
import io.github.sibmaks.jjtemplate.lexer.api.Token;
import io.github.sibmaks.jjtemplate.lexer.api.TokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class JjtemplateGotoDeclarationHandler implements GotoDeclarationHandler {
    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        if (sourceElement == null) {
            return null;
        }
        var file = sourceElement.getContainingFile();
        if (!(file instanceof JjtemplateFile)) {
            return null;
        }

        var text = file.getText();
        List<Token> tokens;
        try {
            tokens = new TemplateLexer(text).tokens();
        } catch (Throwable ignored) {
            return null;
        }

        var templateRanges = buildTemplateRanges(tokens);
        var enclosing = findEnclosingTemplate(templateRanges, offset);
        if (enclosing == null) {
            return null;
        }

        var tokenAtOffset = findIdentifierAtOffset(tokens, enclosing, offset);
        if (tokenAtOffset == null) {
            return null;
        }
        var identifierToken = tokens.get(tokenAtOffset);

        var referencePath = resolveReferencePath(tokens, enclosing, tokenAtOffset);
        var reference = resolveReference(tokens, enclosing, tokenAtOffset);
        if (reference == null) {
            return null;
        }

        var definition = resolveDefinition(text, tokens, templateRanges, reference, referencePath, offset);
        if (definition == null) {
            ApplicationManager.getApplication().invokeLater(
                    () -> HintManager.getInstance().showErrorHint(
                            editor,
                            "Context-bound variable: '" + reference + "'",
                            identifierToken.start,
                            Math.max(identifierToken.end, identifierToken.start + 1),
                            HintManager.ABOVE,
                            HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE
                                    | HintManager.HIDE_BY_SCROLLING | HintManager.HIDE_BY_ESCAPE
                                    | HintManager.HIDE_BY_CARET_MOVE,
                            0
                    )
            );
            return PsiElement.EMPTY_ARRAY;
        }

        var target = file.findElementAt(definition.start());
        if (target != null && target.getTextRange().getStartOffset() == definition.start()) {
            return new PsiElement[]{target};
        }

        return new PsiElement[]{
                new OffsetNavigationElement(
                        file,
                        definition.start(),
                        definition.end(),
                        reference,
                        buildDefinitionPreview(text, definition)
                )
        };
    }

    @Override
    public @Nullable String getActionText(@NotNull DataContext context) {
        return null;
    }

    private static List<TemplateRange> buildTemplateRanges(List<Token> tokens) {
        var result = new ArrayList<TemplateRange>();
        var stack = new ArrayDeque<Integer>();
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.type == TokenType.OPEN_EXPR || token.type == TokenType.OPEN_COND || token.type == TokenType.OPEN_SPREAD) {
                stack.push(i);
                continue;
            }
            if (token.type == TokenType.CLOSE && !stack.isEmpty()) {
                var openIndex = stack.pop();
                var open = tokens.get(openIndex);
                result.add(new TemplateRange(openIndex, i, open.start, token.end));
            }
        }
        return result;
    }

    private static TemplateRange findEnclosingTemplate(List<TemplateRange> ranges, int offset) {
        TemplateRange match = null;
        for (var range : ranges) {
            if (offset >= range.start() && offset < range.end()) {
                if (match == null || range.start() >= match.start()) {
                    match = range;
                }
            }
        }
        return match;
    }

    private static Integer findIdentifierAtOffset(List<Token> tokens, TemplateRange range, int offset) {
        for (int i = range.openTokenIndex() + 1; i < range.closeTokenIndex(); i++) {
            var token = tokens.get(i);
            if (token.type != TokenType.IDENT) {
                continue;
            }
            if (offset >= token.start && offset < token.end) {
                return i;
            }
        }
        return null;
    }

    private static String resolveReference(List<Token> tokens, TemplateRange range, int identTokenIndex) {
        // For .a.b.c resolve any clicked segment to root 'a'.
        var idx = identTokenIndex;
        while (idx - 2 > range.openTokenIndex()
                && tokens.get(idx - 1).type == TokenType.DOT
                && tokens.get(idx - 2).type == TokenType.IDENT) {
            idx -= 2;
        }
        if (idx - 1 > range.openTokenIndex() && tokens.get(idx - 1).type == TokenType.DOT) {
            return tokens.get(idx).lexeme;
        }

        // Also allow ctrl+click on definition names directly.
        var next = nextNonTextToken(tokens, identTokenIndex + 1, range.closeTokenIndex());
        if (next != null && next.type == TokenType.KEYWORD
                && (Keyword.SWITCH.eq(next.lexeme) || Keyword.RANGE.eq(next.lexeme))) {
            return tokens.get(identTokenIndex).lexeme;
        }
        return null;
    }

    private static List<String> resolveReferencePath(List<Token> tokens, TemplateRange range, int identTokenIndex) {
        var start = identTokenIndex;
        while (start - 2 > range.openTokenIndex()
                && tokens.get(start - 1).type == TokenType.DOT
                && tokens.get(start - 2).type == TokenType.IDENT) {
            start -= 2;
        }
        if (start - 1 <= range.openTokenIndex() || tokens.get(start - 1).type != TokenType.DOT) {
            return null;
        }
        var result = new ArrayList<String>();
        for (int i = start; i <= identTokenIndex; i += 2) {
            result.add(tokens.get(i).lexeme);
        }
        return result;
    }

    private static Token nextNonTextToken(List<Token> tokens, int fromInclusive, int toExclusive) {
        for (int i = fromInclusive; i < toExclusive; i++) {
            var token = tokens.get(i);
            if (token.type != TokenType.TEXT) {
                return token;
            }
        }
        return null;
    }

    private static Definition resolveDefinition(String text,
                                                List<Token> tokens,
                                                List<TemplateRange> ranges,
                                                String reference,
                                                List<String> referencePath,
                                                int usageOffset) {
        Definition bestRangeBinding = null;
        Definition bestNamedDefinition = null;

        for (var range : ranges) {
            // named switch/range definition: <IDENT> <KEYWORD switch|range>
            for (int i = range.openTokenIndex() + 1; i + 1 < range.closeTokenIndex(); i++) {
                var first = tokens.get(i);
                var second = tokens.get(i + 1);
                if (first.type == TokenType.IDENT
                        && second.type == TokenType.KEYWORD
                        && first.lexeme.equals(reference)
                        && (Keyword.SWITCH.eq(second.lexeme) || Keyword.RANGE.eq(second.lexeme))
                        && first.start <= usageOffset) {
                    if (bestNamedDefinition == null || first.start >= bestNamedDefinition.start()) {
                        bestNamedDefinition = new Definition(first.start, first.end);
                    }
                }
            }

            // range bindings: range <item>, <index> of ...
            for (int i = range.openTokenIndex() + 5; i < range.closeTokenIndex(); i++) {
                var kw = tokens.get(i - 5);
                var item = tokens.get(i - 4);
                var comma = tokens.get(i - 3);
                var index = tokens.get(i - 2);
                var of = tokens.get(i - 1);

                if (kw.type != TokenType.KEYWORD || !Keyword.RANGE.eq(kw.lexeme)) {
                    continue;
                }
                if (item.type != TokenType.IDENT || comma.type != TokenType.COMMA || index.type != TokenType.IDENT) {
                    continue;
                }
                if (of.type != TokenType.KEYWORD || !Keyword.OF.eq(of.lexeme)) {
                    continue;
                }

                Token binding = null;
                if (item.lexeme.equals(reference)) {
                    binding = item;
                } else if (index.lexeme.equals(reference)) {
                    binding = index;
                }
                if (binding == null || binding.start > usageOffset) {
                    continue;
                }

                var valueScope = findDefinitionValueScope(text, range.end());
                if (valueScope == null) {
                    continue;
                }
                if (usageOffset < valueScope.start() || usageOffset >= valueScope.end()) {
                    continue;
                }
                if (bestRangeBinding == null || binding.start >= bestRangeBinding.start()) {
                    bestRangeBinding = new Definition(binding.start, binding.end);
                }
            }
        }

        if (bestRangeBinding != null) {
            return bestRangeBinding;
        }
        if (bestNamedDefinition != null) {
            return bestNamedDefinition;
        }
        if (referencePath != null && !referencePath.isEmpty()) {
            var nested = findDefinitionsPathDefinition(text, referencePath, usageOffset);
            if (nested != null) {
                return nested;
            }
        }
        return findDefinitionsKeyDefinition(text, reference, usageOffset);
    }

    private static TextSpan findDefinitionValueScope(String text, int templateEndOffset) {
        var length = text.length();
        var i = templateEndOffset;
        while (i < length && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        while (i < length && text.charAt(i) != ':' && text.charAt(i) != '\n' && text.charAt(i) != '\r') {
            i++;
        }
        if (i >= length || text.charAt(i) != ':') {
            return null;
        }
        i++;
        while (i < length && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        if (i >= length) {
            return null;
        }

        var start = i;
        var open = text.charAt(i);
        if (open == '{' || open == '[') {
            var close = open == '{' ? '}' : ']';
            var depth = 1;
            var inString = false;
            var escaped = false;
            var inTemplate = false;

            for (i = i + 1; i < length; i++) {
                var ch = text.charAt(i);
                if (inTemplate) {
                    if (ch == '}' && i + 1 < length && text.charAt(i + 1) == '}') {
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
                    }
                    continue;
                }
                if (ch == '{' && i + 1 < length && text.charAt(i + 1) == '{') {
                    inTemplate = true;
                    i++;
                    continue;
                }
                if (ch == '"') {
                    inString = true;
                    continue;
                }
                if (ch == open) {
                    depth++;
                    continue;
                }
                if (ch == close) {
                    depth--;
                    if (depth == 0) {
                        return new TextSpan(start, i + 1);
                    }
                }
            }
            return null;
        }

        var end = i;
        while (end < length && text.charAt(end) != ',' && text.charAt(end) != '\n' && text.charAt(end) != '\r') {
            end++;
        }
        return new TextSpan(start, end);
    }

    private static Definition findDefinitionsKeyDefinition(String text, String reference, int usageOffset) {
        var definitionsKeyIndex = text.indexOf("\"definitions\"");
        if (definitionsKeyIndex < 0) {
            return null;
        }
        var arrayStart = findNextChar(text, definitionsKeyIndex + 13, '[');
        if (arrayStart < 0) {
            return null;
        }
        var arrayEnd = findMatchingBracket(text, arrayStart, '[', ']');
        if (arrayEnd < 0) {
            return null;
        }

        Definition best = null;
        for (int i = arrayStart + 1; i < arrayEnd; i++) {
            if (text.charAt(i) != '"') {
                continue;
            }
            var stringEnd = findStringEnd(text, i);
            if (stringEnd < 0) {
                break;
            }
            var key = text.substring(i + 1, stringEnd);
            var colonIndex = findNextNonWs(text, stringEnd + 1, arrayEnd);
            if (colonIndex >= 0
                    && text.charAt(colonIndex) == ':'
                    && key.equals(reference)
                    && i + 1 <= usageOffset) {
                best = new Definition(i + 1, stringEnd);
            }
            i = stringEnd;
        }
        return best;
    }

    private static Definition findDefinitionsPathDefinition(String text, List<String> path, int usageOffset) {
        if (path.isEmpty()) {
            return null;
        }
        var definitionsKeyIndex = text.indexOf("\"definitions\"");
        if (definitionsKeyIndex < 0) {
            return null;
        }
        var arrayStart = findNextChar(text, definitionsKeyIndex + 13, '[');
        if (arrayStart < 0) {
            return null;
        }
        var arrayEnd = findMatchingBracket(text, arrayStart, '[', ']');
        if (arrayEnd < 0) {
            return null;
        }

        Definition bestPrior = null;
        Definition first = null;
        for (int i = arrayStart + 1; i < arrayEnd; ) {
            i = skipWsAndCommas(text, i, arrayEnd);
            if (i >= arrayEnd) {
                break;
            }
            var valueEnd = findJsonValueEnd(text, i, arrayEnd);
            if (valueEnd <= i) {
                break;
            }
            if (text.charAt(i) == '{') {
                var found = findKeyPathInObject(text, i, valueEnd, path, 0);
                if (found != null) {
                    if (first == null) {
                        first = found;
                    }
                    if (found.start() <= usageOffset && (bestPrior == null || found.start() > bestPrior.start())) {
                        bestPrior = found;
                    }
                }
            }
            i = valueEnd;
        }
        return bestPrior != null ? bestPrior : first;
    }

    private static Definition findKeyPathInObject(String text,
                                                  int objectStart,
                                                  int objectEndExclusive,
                                                  List<String> path,
                                                  int depth) {
        if (depth >= path.size()) {
            return null;
        }
        for (int i = objectStart + 1; i < objectEndExclusive; ) {
            i = skipWsAndCommas(text, i, objectEndExclusive);
            if (i >= objectEndExclusive || text.charAt(i) == '}') {
                break;
            }
            if (text.charAt(i) != '"') {
                i++;
                continue;
            }

            var keyEnd = findStringEnd(text, i);
            if (keyEnd < 0 || keyEnd >= objectEndExclusive) {
                break;
            }
            var key = text.substring(i + 1, keyEnd);
            var colon = findNextNonWs(text, keyEnd + 1, objectEndExclusive);
            if (colon < 0 || text.charAt(colon) != ':') {
                i = keyEnd + 1;
                continue;
            }

            var valueStart = findNextNonWs(text, colon + 1, objectEndExclusive);
            if (valueStart < 0) {
                break;
            }
            var valueEnd = findJsonValueEnd(text, valueStart, objectEndExclusive);
            if (valueEnd <= valueStart) {
                break;
            }

            if (path.get(depth).equals(key)) {
                if (depth == path.size() - 1) {
                    return new Definition(i + 1, keyEnd);
                }
                if (text.charAt(valueStart) == '{') {
                    var nested = findKeyPathInObject(text, valueStart, valueEnd, path, depth + 1);
                    if (nested != null) {
                        return nested;
                    }
                }
            }

            i = valueEnd;
        }
        return null;
    }

    private static int skipWsAndCommas(String text, int fromInclusive, int toExclusive) {
        var i = fromInclusive;
        while (i < toExclusive) {
            var ch = text.charAt(i);
            if (Character.isWhitespace(ch) || ch == ',') {
                i++;
                continue;
            }
            break;
        }
        return i;
    }

    private static int findJsonValueEnd(String text, int valueStart, int limitExclusive) {
        if (valueStart >= limitExclusive) {
            return valueStart;
        }
        var start = text.charAt(valueStart);
        if (start == '{') {
            var end = findMatchingBracket(text, valueStart, '{', '}');
            return end < 0 ? valueStart : Math.min(end + 1, limitExclusive);
        }
        if (start == '[') {
            var end = findMatchingBracket(text, valueStart, '[', ']');
            return end < 0 ? valueStart : Math.min(end + 1, limitExclusive);
        }
        if (start == '"') {
            var end = findStringEnd(text, valueStart);
            return end < 0 ? valueStart : Math.min(end + 1, limitExclusive);
        }
        var i = valueStart;
        while (i < limitExclusive) {
            var ch = text.charAt(i);
            if (ch == ',' || ch == '}' || ch == ']') {
                break;
            }
            i++;
        }
        return i;
    }

    private static int findNextChar(String text, int fromInclusive, char expected) {
        for (int i = fromInclusive; i < text.length(); i++) {
            if (text.charAt(i) == expected) {
                return i;
            }
        }
        return -1;
    }

    private static int findNextNonWs(String text, int fromInclusive, int toExclusive) {
        for (int i = fromInclusive; i < toExclusive; i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int findMatchingBracket(String text, int startIndex, char open, char close) {
        var depth = 1;
        var inString = false;
        var escaped = false;
        for (int i = startIndex + 1; i < text.length(); i++) {
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
            if (ch == '"') {
                inString = true;
                continue;
            }
            if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int findStringEnd(String text, int stringStartQuoteOffset) {
        var escaped = false;
        for (int i = stringStartQuoteOffset + 1; i < text.length(); i++) {
            var ch = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                return i;
            }
        }
        return -1;
    }

    private static String buildDefinitionPreview(String text, Definition definition) {
        var fallback = abbreviate(text.substring(definition.start(), definition.end()).trim(), 200);
        var scanLimit = text.length();
        for (int i = definition.end(); i < text.length(); i++) {
            var ch = text.charAt(i);
            if (ch == '\n' || ch == '\r') {
                scanLimit = i;
                break;
            }
        }
        var colon = findNextChar(text, definition.end(), ':');
        if (colon < 0 || colon >= scanLimit) {
            return fallback;
        }
        var valueStart = findNextNonWs(text, colon + 1, text.length());
        if (valueStart < 0) {
            return fallback;
        }
        var valueEnd = findJsonValueEnd(text, valueStart, text.length());
        if (valueEnd <= valueStart) {
            return fallback;
        }
        return abbreviate(text.substring(valueStart, valueEnd).trim(), 220);
    }

    private static String abbreviate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private record TemplateRange(int openTokenIndex, int closeTokenIndex, int start, int end) {
    }

    private record Definition(int start, int end) {
    }

    private record TextSpan(int start, int end) {
    }

    public static final class OffsetNavigationElement extends FakePsiElement {
        private final PsiFile file;
        private final int startOffset;
        private final int endOffsetExclusive;
        private final String variableName;
        private final String previewText;

        private OffsetNavigationElement(PsiFile file,
                                        int startOffset,
                                        int endOffsetExclusive,
                                        String variableName,
                                        String previewText) {
            this.file = file;
            this.startOffset = startOffset;
            this.endOffsetExclusive = endOffsetExclusive;
            this.variableName = variableName;
            this.previewText = previewText;
        }

        @Override
        public PsiElement getParent() {
            return file;
        }

        @Override
        public PsiFile getContainingFile() {
            return file;
        }

        @Override
        public TextRange getTextRange() {
            var length = Math.max(1, endOffsetExclusive - startOffset);
            return TextRange.from(startOffset, length);
        }

        @Override
        public int getTextOffset() {
            return startOffset;
        }

        @Override
        public String getName() {
            return variableName;
        }

        @Override
        public String getText() {
            return previewText;
        }

        public String getPreviewText() {
            return previewText;
        }

        @Override
        public ItemPresentation getPresentation() {
            return new ItemPresentation() {
                @Override
                public String getPresentableText() {
                    return variableName;
                }

                @Override
                public String getLocationString() {
                    return previewText;
                }

                @Override
                public javax.swing.Icon getIcon(boolean unused) {
                    return null;
                }
            };
        }

        @Override
        public void navigate(boolean requestFocus) {
            var virtualFile = file.getVirtualFile();
            if (virtualFile == null) {
                return;
            }
            new OpenFileDescriptor(file.getProject(), virtualFile, startOffset).navigate(requestFocus);
        }

        @Override
        public boolean canNavigate() {
            return true;
        }

        @Override
        public boolean canNavigateToSource() {
            return true;
        }

        @Override
        public @NotNull Project getProject() {
            return file.getProject();
        }

        @Override
        public String toString() {
            return "JJTemplate variable '" + variableName + "'";
        }
    }
}
