package com.whisent.kubeloader.scripts.tserase;

final class TsEraseGenericTypePlugin implements TsErasePlugin {
    @Override
    public String syntax() {
        return "<T>";
    }

    @Override
    public boolean matches(TsEraseContext context) {
        return context.currentChar() == '<' && context.lookIsGeneric();
    }

    @Override
    public boolean apply(TsEraseContext context) {
        int pos = context.position() + 1;
        int depth = 1;

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

        context.position(pos);
        return true;
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
}
