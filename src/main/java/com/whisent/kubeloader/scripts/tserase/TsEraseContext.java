package com.whisent.kubeloader.scripts.tserase;

final class TsEraseContext {
    private final char[] s;
    private final int n;
    private final StringBuilder out = new StringBuilder();
    private int i = 0;

    private boolean inStr = false;
    private boolean inTemplate = false;
    private boolean inLineComment = false;
    private boolean inBlockComment = false;

    TsEraseContext(String src) {
        this.s = src.toCharArray();
        this.n = s.length;
    }

    boolean hasCurrent() {
        return i < n;
    }

    char currentChar() {
        return s[i];
    }

    int position() {
        return i;
    }

    void position(int newPosition) {
        i = newPosition;
    }

    int length() {
        return n;
    }

    char charAt(int index) {
        return s[index];
    }

    void append(char c) {
        out.append(c);
    }

    void advance() {
        i++;
    }

    String output() {
        return out.toString();
    }

    boolean inCommentOrString() {
        return inStr || inTemplate || inLineComment || inBlockComment;
    }

    void updateState(char c) {
        if (inLineComment) {
            if (c == '\n' || c == '\r') {
                inLineComment = false;
            }
            return;
        }

        if (inBlockComment) {
            if (c == '*' && i + 1 < n && s[i + 1] == '/') {
                inBlockComment = false;
                i++;
            }
            return;
        }

        if (!inStr && !inTemplate) {
            if (c == '/' && i + 1 < n) {
                if (s[i + 1] == '/') {
                    inLineComment = true;
                } else if (s[i + 1] == '*') {
                    inBlockComment = true;
                }
            }
        }

        if (!inLineComment && !inBlockComment) {
            if (c == '`') {
                inTemplate = !inTemplate;
            } else if (!inTemplate && (c == '"' || c == '\'')) {
                inStr = !inStr;
            }
        }
    }

    boolean atWord(String word) {
        if (i + word.length() > n) {
            return false;
        }

        for (int j = 0; j < word.length(); j++) {
            if (s[i + j] != word.charAt(j)) {
                return false;
            }
        }

        char prev = i == 0 ? ' ' : s[i - 1];
        char next = i + word.length() >= n ? ' ' : s[i + word.length()];
        return !Character.isJavaIdentifierPart(prev) && !Character.isJavaIdentifierPart(next);
    }

    boolean isStandaloneTypeDecl() {
        if (!atWord("type") && !atWord("interface")) {
            return false;
        }

        int p = i;
        while (p > 0 && s[p - 1] != '\n' && s[p - 1] != '\r') {
            p--;
        }
        while (p < i && Character.isWhitespace(s[p])) {
            p++;
        }
        return p == i;
    }

    boolean lookIsGeneric() {
        if (i + 1 >= n) {
            return false;
        }

        char next = s[i + 1];
        return Character.isUpperCase(next) || next == '_' || next == '$' || next == 'T';
    }
}
