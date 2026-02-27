package io.github.sibmaks.jjtemplate.idea.lang;

import io.github.sibmaks.jjtemplate.lexer.TemplateLexer;
import io.github.sibmaks.jjtemplate.lexer.api.TokenType;

final class TemplateTextScanner {
    private TemplateTextScanner() {
    }

    static boolean isTemplateStart(CharSequence source, int index) {
        if (index < 0 || index + 1 >= source.length() || source.charAt(index) != '{') {
            return false;
        }
        var next = source.charAt(index + 1);
        return next == '{' || next == '?' || next == '.';
    }

    static int findTemplateEnd(CharSequence source, int start, int endExclusive) {
        if (!isTemplateStart(source, start)) {
            return -1;
        }
        var limit = Math.min(Math.max(endExclusive, 0), source.length());
        var candidate = source.subSequence(start, limit).toString();
        try {
            var depth = 0;
            var tokens = new TemplateLexer(candidate).tokens();
            for (var token : tokens) {
                if (token.type == TokenType.OPEN_EXPR
                        || token.type == TokenType.OPEN_COND
                        || token.type == TokenType.OPEN_SPREAD) {
                    depth++;
                    continue;
                }
                if (token.type == TokenType.CLOSE) {
                    depth--;
                    if (depth == 0) {
                        return start + token.end;
                    }
                }
            }
            return -1;
        } catch (Throwable ignored) {
            return findTemplateEndFallback(source, start, limit);
        }
    }

    private static int findTemplateEndFallback(CharSequence source, int start, int limit) {
        var depth = 0;
        for (int i = start; i + 1 < limit; i++) {
            if (isTemplateStart(source, i)) {
                depth++;
                i++;
                continue;
            }
            if (source.charAt(i) == '}' && source.charAt(i + 1) == '}') {
                depth--;
                i++;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return -1;
    }
}
