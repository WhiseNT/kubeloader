package com.whisent.kubeloader.scripts.tserase;

final class TsEraseStandaloneTypeDeclarationPlugin implements TsErasePlugin {
    @Override
    public String syntax() {
        return "type Foo = ...";
    }

    @Override
    public boolean matches(TsEraseContext context) {
        return context.isStandaloneTypeDecl();
    }

    @Override
    public boolean apply(TsEraseContext context) {
        int startLine = lineStart(context, context.position());
        int end = skipDeclaration(context, context.position());
        appendCommentedRange(context, startLine, end);
        context.position(end);
        return true;
    }

    private static int lineStart(TsEraseContext context, int pos) {
        while (pos > 0) {
            char c = context.charAt(pos - 1);
            if (c == '\n' || c == '\r') {
                break;
            }
            pos--;
        }
        return pos;
    }

    private static int skipDeclaration(TsEraseContext context, int pos) {
        pos += context.atWord("type") ? 4 : 9;

        pos = skipWhitespace(context, pos);
        pos = skipIdentifier(context, pos);
        pos = skipWhitespace(context, pos);

        if (pos < context.length() && context.charAt(pos) == '<') {
            pos = skipAngleBlock(context, pos);
            pos = skipWhitespace(context, pos);
        }

        if (startsWithWord(context, pos, "extends")) {
            pos += 7;
            pos = skipWhitespace(context, pos);
            while (pos < context.length()) {
                char c = context.charAt(pos);
                if (c == '{' || c == ';' || c == '=') {
                    break;
                }
                if (c == '<') {
                    pos = skipAngleBlock(context, pos);
                    continue;
                }
                if (c == '\'' || c == '"' || c == '`') {
                    pos = skipQuoted(context, pos);
                    continue;
                }
                pos++;
            }
            pos = skipWhitespace(context, pos);
        }

        while (pos < context.length()) {
            char c = context.charAt(pos);
            if (c == '{' || c == ';') {
                break;
            }
            if (c == '\'' || c == '"' || c == '`') {
                pos = skipQuoted(context, pos);
                continue;
            }
            pos++;
        }

        if (pos < context.length() && context.charAt(pos) == '{') {
            pos = skipBraces(context, pos);
        } else if (pos < context.length() && context.charAt(pos) == ';') {
            pos++;
        }

        return pos;
    }

    private static int skipWhitespace(TsEraseContext context, int pos) {
        while (pos < context.length() && Character.isWhitespace(context.charAt(pos))) {
            pos++;
        }
        return pos;
    }

    private static int skipIdentifier(TsEraseContext context, int pos) {
        while (pos < context.length()) {
            char c = context.charAt(pos);
            if (Character.isJavaIdentifierPart(c) || c == '$' || c == '_') {
                pos++;
            } else {
                break;
            }
        }
        return pos;
    }

    private static int skipAngleBlock(TsEraseContext context, int pos) {
        int depth = 1;
        pos++;
        while (pos < context.length() && depth > 0) {
            char c = context.charAt(pos);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == '\'' || c == '"' || c == '`') {
                pos = skipQuoted(context, pos);
                continue;
            }
            pos++;
        }
        return pos;
    }

    private static int skipBraces(TsEraseContext context, int pos) {
        int depth = 1;
        pos++;
        while (pos < context.length() && depth > 0) {
            char c = context.charAt(pos);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            } else if (c == '\'' || c == '"' || c == '`') {
                pos = skipQuoted(context, pos);
                continue;
            }
            pos++;
        }
        return pos;
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

    private static boolean startsWithWord(TsEraseContext context, int pos, String word) {
        if (pos + word.length() > context.length()) {
            return false;
        }
        for (int i = 0; i < word.length(); i++) {
            if (context.charAt(pos + i) != word.charAt(i)) {
                return false;
            }
        }
        char prev = pos == 0 ? ' ' : context.charAt(pos - 1);
        char next = pos + word.length() >= context.length() ? ' ' : context.charAt(pos + word.length());
        return !Character.isJavaIdentifierPart(prev) && !Character.isJavaIdentifierPart(next);
    }

    private static void appendCommentedRange(TsEraseContext context, int from, int to) {
        for (int i = from; i < to; i++) {
            if (i == from || context.charAt(i - 1) == '\n' || context.charAt(i - 1) == '\r') {
                context.append('/') ;
                context.append('/');
                context.append(' ');
            }
            context.append(context.charAt(i));
        }
    }
}
