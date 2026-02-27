package io.github.sibmaks.jjtemplate.idea.lang;

import java.util.ArrayDeque;

public final class JjtemplateJsonLikeFormatter {
    private JjtemplateJsonLikeFormatter() {
    }

    public static String format(String source, int indentSize) {
        if (source == null || source.isEmpty()) {
            return "";
        }
        var indentStep = Math.max(indentSize, 1);
        var out = new StringBuilder(source.length() + 32);
        var indentLevel = 0;
        var indentAppliedByBracket = new ArrayDeque<Boolean>();
        var index = 0;
        while (index < source.length()) {
            var ch = source.charAt(index);
            if (Character.isWhitespace(ch)) {
                index++;
                continue;
            }

            if (ch == '"') {
                index = appendString(source, index, out);
                continue;
            }
            if (TemplateTextScanner.isTemplateStart(source, index)) {
                index = appendTemplateBlock(source, index, out);
                continue;
            }

            switch (ch) {
                case '{', '[' -> {
                    out.append(ch);
                    var close = ch == '{' ? '}' : ']';
                    var next = nextSignificantIndex(source, index + 1);
                    if (next >= source.length() || source.charAt(next) == close) {
                        indentAppliedByBracket.addLast(false);
                        index++;
                        continue;
                    }
                    indentAppliedByBracket.addLast(true);
                    indentLevel++;
                    appendNewlineWithIndent(out, indentLevel, indentStep);
                    index++;
                }
                case '}', ']' -> {
                    var indented = !indentAppliedByBracket.isEmpty() && indentAppliedByBracket.removeLast();
                    if (indented) {
                        indentLevel = Math.max(0, indentLevel - 1);
                        appendNewlineIfNeeded(out);
                        appendIndent(out, indentLevel, indentStep);
                    }
                    out.append(ch);
                    index++;
                }
                case ',' -> {
                    out.append(',');
                    appendNewlineWithIndent(out, indentLevel, indentStep);
                    index++;
                }
                case ':' -> {
                    out.append(':').append(' ');
                    index++;
                }
                default -> {
                    out.append(ch);
                    index++;
                }
            }
        }
        return out.toString();
    }

    private static int appendString(String source, int from, StringBuilder out) {
        var index = from;
        var escaped = false;
        while (index < source.length()) {
            var ch = source.charAt(index);
            out.append(ch);
            if (escaped) {
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '"' && index > from) {
                return index + 1;
            }
            index++;
        }
        return index;
    }

    private static int appendTemplateBlock(String source, int from, StringBuilder out) {
        var templateEnd = TemplateTextScanner.findTemplateEnd(source, from, source.length());
        if (templateEnd < 0) {
            out.append(source, from, source.length());
            return source.length();
        }
        out.append(source, from, templateEnd);
        return templateEnd;
    }

    private static int nextSignificantIndex(String source, int from) {
        var index = from;
        while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
            index++;
        }
        return index;
    }

    private static void appendNewlineIfNeeded(StringBuilder out) {
        if (out.isEmpty()) {
            return;
        }
        if (out.charAt(out.length() - 1) == '\n') {
            return;
        }
        out.append('\n');
    }

    private static void appendNewlineWithIndent(StringBuilder out, int indentLevel, int indentStep) {
        appendNewlineIfNeeded(out);
        appendIndent(out, indentLevel, indentStep);
    }

    private static void appendIndent(StringBuilder out, int indentLevel, int indentStep) {
        var spaces = Math.max(0, indentLevel * indentStep);
        out.append(" ".repeat(spaces));
    }

}
