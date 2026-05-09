package com.whisent.kubeloader.scripts.tserase;

final class TsEraseTypeAnnotationPlugin implements TsErasePlugin {
    @Override
    public String syntax() {
        return "foo: T";
    }

    @Override
    public boolean matches(TsEraseContext context) {
        return context.currentChar() == ':';
    }

    @Override
    public boolean apply(TsEraseContext context) {
        int pos = context.position();
        if (isTernaryOperator(context, pos) || isObjectLiteralContext(context, pos) || isArrowFunctionReturnContext(context, pos)) {
            return false;
        }

        pos++;
        while (pos < context.length()) {
            char ch = context.charAt(pos);
            if (ch == '\'' || ch == '"' || ch == '`') {
                pos = skipQuoted(context, pos);
                continue;
            }
            if (ch == '<') {
                pos = skipNested(context, pos, '<', '>');
                continue;
            }
            if (ch == '(') {
                pos = skipNested(context, pos, '(', ')');
                continue;
            }
            if (ch == '[') {
                pos = skipNested(context, pos, '[', ']');
                continue;
            }
            if (Character.isWhitespace(ch)) {
                pos++;
                continue;
            }
            if (ch == ',' || ch == ')' || ch == ';' || ch == '{' || ch == '='
                    || ch == '\n' || ch == '\r' || ch == '>') {
                break;
            }
            if (Character.isJavaIdentifierStart(ch) || ch == '_' || ch == '$'
                    || ch == '|' || ch == '&' || ch == '.' || ch == '?') {
                pos++;
                while (pos < context.length()) {
                    ch = context.charAt(pos);
                    if (Character.isJavaIdentifierPart(ch) || ch == '_' || ch == '$'
                            || ch == ' ' || ch == '\t' || ch == '|'
                            || ch == '&' || ch == '.' || ch == '?'
                            || ch == '!') {
                        pos++;
                    } else {
                        break;
                    }
                }
                continue;
            }
            pos++;
        }

        context.position(pos);
        return true;
    }

    private static boolean isTernaryOperator(TsEraseContext context, int pos) {
        int depth = 0;
        int index = pos - 1;
        while (index >= 0) {
            char ch = context.charAt(index);
            if (ch == ')' || ch == ']' || ch == '}') {
                depth++;
            } else if (ch == '(' || ch == '[' || ch == '{') {
                depth--;
            } else if (depth == 0 && ch == '?') {
                return true;
            }
            index--;
        }
        return false;
    }

    private static boolean isObjectLiteralContext(TsEraseContext context, int pos) {
        int index = pos + 1;
        while (index < context.length() && Character.isWhitespace(context.charAt(index))) {
            index++;
        }
        if (index < context.length()) {
            char next = context.charAt(index);
            return next == '"' || next == '\'' || next == '`'
                    || Character.isDigit(next) || next == '-'
                    || next == '[' || next == '{'
                    || next == 't' || next == 'f' || next == 'n'
                    || next == '+' || next == '.';
        }
        return false;
    }

    private static boolean isArrowFunctionReturnContext(TsEraseContext context, int pos) {
        int index = pos - 1;
        while (index >= 0 && Character.isWhitespace(context.charAt(index))) {
            index--;
        }
        return index > 0 && context.charAt(index) == '>' && context.charAt(index - 1) == '=';
    }

    private static int skipQuoted(TsEraseContext context, int pos) {
        char quote = context.charAt(pos);
        pos++;
        while (pos < context.length()) {
            char c = context.charAt(pos);
            if (c == '\\' && pos + 1 < context.length()) {
                pos += 2;
                continue;
            }
            if (c == quote) {
                pos++;
                break;
            }
            pos++;
        }
        return pos;
    }

    private static int skipNested(TsEraseContext context, int pos, char open, char close) {
        int depth = 1;
        pos++;
        while (pos < context.length() && depth > 0) {
            char c = context.charAt(pos);
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
            } else if (c == '\'' || c == '"' || c == '`') {
                pos = skipQuoted(context, pos);
                continue;
            }
            pos++;
        }
        return pos;
    }
}
