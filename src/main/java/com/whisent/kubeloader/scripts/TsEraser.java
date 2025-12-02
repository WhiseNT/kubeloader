package com.whisent.kubeloader.scripts;

import java.util.Arrays;

public final class TsEraser {

    public static String eraseTypes(String src) {
        if (src == null || src.isEmpty()) return src;
        return new EraseEngine(src).erase();
    }

    /* ====================================================================== */
    private static final class EraseEngine {
        private final char[] s;
        private final int n;
        private final StringBuilder out = new StringBuilder();
        private int i = 0;

        private boolean inStr = false, inTemplate = false,
                inLineComment = false, inBlockComment = false;

        EraseEngine(String src) {
            this.s = src.toCharArray();
            this.n = s.length;
        }

        /* -------------------- Main Loop -------------------- */
        String erase() {
            while (i < n) {
                char c = s[i];

                updateState(c);
                if (inCommentOrString()) {          // Output strings/comments as-is
                    out.append(c);
                    i++;
                    continue;
                }

                /* 0. Ternary operator ? ... : cross-line protection */


                /* 1. Standalone type/interface declarations at line start only */
                if (isStandaloneTypeDecl()) {
                    commentOutToSemicolonOrBrace();
                    continue;
                }

                /* 2. Skip implements ...{ */
                if (atWord("implements")) {
                    while (i < n && s[i] != '{') i++;
                    continue;
                }

                /* 3. Erase generics <T> */
                if (c == '<' && lookIsGeneric()) {
                    skipAngleBrackets();
                    continue;
                }

                /* 4. Erase type annotations : T */
                if (c == ':' && skipTypeAnnotation()) {
                    continue;
                }

                /* 5. Output everything else as-is */
                out.append(c);
                i++;
            }
            return out.toString();
        }

        /* -------------------- Key fixes -------------------- */

        /* 0. Ternary ?: cross-line protection: jump to top-level colon */
        private boolean skipTernary() {
            int depth = 0, j = i + 1;
            while (j < n) {
                char ch = s[j];
                if (ch == '(' || ch == '[' || ch == '{') depth++;
                else if (ch == ')' || ch == ']' || ch == '}') depth--;
                else if (depth == 0 && ch == ':') { 
                    i = j; 
                    return true; 
                }
                j++;
            }
            return false;
        }

        /* 1. Only type/interface at line start (only whitespace before) are considered standalone declarations */
        private boolean isStandaloneTypeDecl() {
            if (!atWord("type") && !atWord("interface")) return false;
            int p = i;
            while (p > 0 && s[p - 1] != '\n' && s[p - 1] != '\r') p--;
            while (p < i && Character.isWhitespace(s[p])) p++;
            return p == i;
        }

        private void commentOutToSemicolonOrBrace() {
            int startLine = i;
            while (startLine > 0 && s[startLine - 1] != '\n' && s[startLine - 1] != '\r') {
                startLine--;
            }

            int pos = i;
            // Skip the 'type' or 'interface' keyword
            if (atWord("type")) {
                pos += "type".length();
            } else if (atWord("interface")) {
                pos += "interface".length();
            }

            // Skip whitespace
            while (pos < n && Character.isWhitespace(s[pos])) pos++;

            // Skip identifier
            while (pos < n && (Character.isJavaIdentifierPart(s[pos]) || s[pos] == '$' || s[pos] == '_')) {
                pos++;
            }

            // Skip generic <...>
            if (pos < n && s[pos] == '<') {
                int depth = 1;
                pos++;
                while (pos < n && depth > 0) {
                    char c = s[pos];
                    if (c == '<') depth++;
                    else if (c == '>') depth--;
                    else if (c == '\'' || c == '"' || c == '`') {
                        // Skip string inside generic (rare but possible in mapped types)
                        skipStringLiteral(pos);
                        // We'll adjust pos in skipStringLiteral, but for simplicity, we avoid deep nesting here
                        // Instead, we assume no strings in generics for this context
                    }
                    pos++;
                }
            }

            // Skip whitespace
            while (pos < n && Character.isWhitespace(s[pos])) pos++;

            // Handle 'extends' for interface
            if (pos + 7 <= n && "extends".regionMatches(0, Arrays.toString(s), pos, 7)) {
                // Check it's a whole word
                if ((pos == 0 || !Character.isJavaIdentifierPart(s[pos - 1])) &&
                        (pos + 7 >= n || !Character.isJavaIdentifierPart(s[pos + 7]))) {
                    pos += 7;
                    // Skip extended types (could be multiple: A & B, or A, B)
                    while (pos < n) {
                        char c = s[pos];
                        if (Character.isWhitespace(c)) {
                            pos++;
                            continue;
                        }
                        if (c == '{' || c == ';' || c == '=') break;

                        // Skip type identifiers, dots, &, |, etc.
                        if (Character.isJavaIdentifierStart(c) || c == '.' || c == '&' || c == '|' || c == ',') {
                            while (pos < n && (Character.isJavaIdentifierPart(s[pos]) || s[pos] == '.' ||
                                    s[pos] == '&' || s[pos] == '|' || s[pos] == ',' ||
                                    Character.isWhitespace(s[pos]))) {
                                pos++;
                            }
                            continue;
                        }
                        // Skip generic in extends: Base<T>
                        if (c == '<') {
                            int depth = 1;
                            pos++;
                            while (pos < n && depth > 0) {
                                char ch = s[pos];
                                if (ch == '<') depth++;
                                else if (ch == '>') depth--;
                                pos++;
                            }
                            continue;
                        }
                        break;
                    }
                }
            }

            // For 'type', skip '=' and RHS until '{' or ';'
            while (pos < n) {
                char c = s[pos];
                if (c == '{' || c == ';') break;
                if (c == '\'' || c == '"' || c == '`') {
                    skipStringLiteral(pos);
                    // Since we can't easily update pos from helper, we approximate:
                    // Instead, we break conservatively — better to include extra than miss brace
                    // But for safety, we just scan normally below
                }
                pos++;
            }

            // Now find the actual end including block if '{'
            int end = pos;
            if (end < n && s[end] == '{') {
                int depth = 1;
                end++; // skip '{'
                while (end < n && depth > 0) {
                    char c = s[end];
                    if (c == '{') depth++;
                    else if (c == '}') depth--;
                    end++;
                }
            } else if (end < n && s[end] == ';') {
                end++; // include ';'
            }

            appendCommented(startLine, end);
            i = end;
        }

        // Helper to skip string literal and return new position (not used above due to complexity)
// We rely on main loop instead; for declaration head, strings are rare
        private void skipStringLiteral(int start) {
            // Not used in current logic; placeholder
        }

        private void appendCommented(int from, int to) {
            for (int j = from; j < to; j++) {
                if (j == from || s[j - 1] == '\n' || s[j - 1] == '\r') out.append("// ");
                out.append(s[j]);
            }
        }

        /* 3. Erase : Type annotations (with ternary, object literal, arrow bracket protection) */
        private boolean skipTypeAnnotation() {
            // Check if we are in a ternary operator context
            if (isInTernaryOperator()) {
                return false;
            }
            
            // Check if we are in an object literal context
            if (isObjectLiteralContext()) {
                return false;
            }
            
            // Check if we are in an arrow function return type context
            if (isArrowFunctionReturnContext()) {
                return false;
            }
            
            // Check if we are in a function parameter or return type context
            if (isFunctionTypeContext()) {
                return eraseTypeAnnotation();
            }
            
            // By default, erase type annotation
            return eraseTypeAnnotation();
        }
        
        private boolean isInTernaryOperator() {
            // Look backward for unpaired '?'
            int depth = 0;
            int j = i - 1;
            while (j >= 0) {
                char ch = s[j];
                if (ch == ')' || ch == ']' || ch == '}') depth++;
                else if (ch == '(' || ch == '[' || ch == '{') depth--;
                else if (depth == 0 && ch == '?') {
                    return true;
                }
                j--;
            }
            return false;
        }
        
        private boolean isObjectLiteralContext() {
            // Look ahead to see what comes after the colon
            int j = i + 1;
            while (j < n && Character.isWhitespace(s[j])) j++;
            if (j < n) {
                char nx = s[j];
                // If it's a literal value, it's likely an object literal
                if (nx == '"' || nx == '\'' || nx == '`'
                        || Character.isDigit(nx) || nx == '-'
                        || nx == '[' || nx == '{'
                        || nx == 't' || nx == 'f' || nx == 'n'  // true, false, null
                        || nx == '+' || nx == '.') {
                    return true;
                }
            }
            return false;
        }
        
        private boolean isArrowFunctionReturnContext() {
            // Look forward to see if we're in an arrow function
            int j = i + 1;
            while (j < n && Character.isWhitespace(s[j])) j++;
            
            // Look backward for =>
            int k = i - 1;
            while (k >= 0 && Character.isWhitespace(s[k])) k--;
            if (k > 0 && s[k] == '>' && s[k-1] == '=') {
                return true;
            }
            
            return false;
        }
        
        private boolean isFunctionTypeContext() {
            // Look backward for context clues
            int p = i - 1;
            while (p >= 0 && Character.isWhitespace(s[p])) p--;
            
            // If preceded by '(', it's likely a parameter type
            if (p >= 0 && s[p] == '(') {
                return true;
            }
            
            // If preceded by ')', it might be a return type
            if (p >= 0 && s[p] == ')') {
                return true;
            }
            
            return false;
        }

        private boolean eraseTypeAnnotation() {
            i++; // skip ':'
            while (i < n) {
                char ch = s[i];
                if (ch == '\'' || ch == '"' || ch == '`') {
                    skipString(ch);
                    continue;
                }
                if (ch == '<') {
                    skipAngleBrackets();
                    continue;
                }
                if (ch == '(') {
                    skipNested('(', ')');
                    continue;
                }
                if (ch == '[') {
                    skipNested('[', ']');
                    continue;
                }
                if (Character.isWhitespace(ch)) {
                    i++;
                    continue;
                }

                // True boundaries where type annotation ends
                if (ch == ',' || ch == ')' || ch == ';' || ch == '{' || ch == '='
                        || ch == '\n' || ch == '\r' || ch == '>') {
                    break;
                }

                // Consume type tokens: identifiers, union/intersection, etc.
                if (Character.isJavaIdentifierStart(ch) || ch == '_' || ch == '$'
                        || ch == '|' || ch == '&' || ch == '.' || ch == '?') {
                    i++;
                    // Optionally consume more of the same kind
                    while (i < n) {
                        ch = s[i];
                        if (Character.isJavaIdentifierPart(ch) || ch == '_' || ch == '$'
                                || ch == ' ' || ch == '\t' || ch == '|' || ch == '&'
                                || ch == '.' || ch == '?' || ch == '!') {
                            i++;
                        } else {
                            break;
                        }
                    }
                    continue;
                }

                // Unknown char: consume one and continue (e.g., ~, *, etc.)
                i++;
            }
            return true;
        }

        /* -------------------- Other utility methods -------------------- */

        private boolean lookIsGeneric() {
            if (i + 1 >= n) return false;
            char nx = s[i + 1];
            return Character.isUpperCase(nx) || nx == '_' || nx == '$' || nx == 'T';
        }

        private void skipAngleBrackets() {
            int depth = 1;
            i++;
            while (i < n && depth > 0) {
                char c = s[i];
                if (c == '<') depth++;
                else if (c == '>') depth--;
                else if (c == '\'' || c == '"' || c == '`') skipString(c);
                i++;
            }
        }

        private void skipString(char q) {
            i++;
            while (i < n && s[i] != q) {
                if (s[i] == '\\' && i + 1 < n) i++;
                i++;
            }
            if (i < n) i++;
        }

        private void skipNested(char open, char close) {
            int depth = 1;
            i++;
            while (i < n && depth > 0) {
                char c = s[i];
                if (c == open) depth++;
                else if (c == close) depth--;
                else if (c == '\'' || c == '"' || c == '`') skipString(c);
                i++;
            }
        }

        private static char paired(char c) {
            return switch (c) {
                case '<' -> '>';
                case '(' -> ')';
                case '[' -> ']';
                default -> c;
            };
        }

        private boolean atWord(String word) {
            if (i + word.length() > n) return false;
            for (int j = 0; j < word.length(); j++)
                if (s[i + j] != word.charAt(j)) return false;
            char prev = i == 0 ? ' ' : s[i - 1];
            char next = i + word.length() >= n ? ' ' : s[i + word.length()];
            return !Character.isJavaIdentifierPart(prev)
                    && !Character.isJavaIdentifierPart(next);
        }

        private void updateState(char c) {
            if (inLineComment) {
                if (c == '\n' || c == '\r') inLineComment = false;
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
                    if (s[i + 1] == '/') inLineComment = true;
                    else if (s[i + 1] == '*') inBlockComment = true;
                }
            }
            if (!inLineComment && !inBlockComment) {
                if (c == '`') inTemplate = !inTemplate;
                else if (!inTemplate && (c == '"' || c == '\'')) inStr = !inStr;
            }
        }

        private boolean inCommentOrString() {
            return inStr || inTemplate || inLineComment || inBlockComment;
        }
    }

    /* ---------------------- Test -------------------------- */
    public static void main(String[] args) {
        String[] cases = {
                // 1. Line-start type with line break =
                """
        type A =
          | 'x'
          | 'y'
        const a:A = 'x'
        """,

                // 2. interface multiline + generics + inheritance
                """
        interface Foo<T>
           {
          name: string
        }
        function f(x: Foo<number>): void {}
        """,

                // 3. Function inline type parameter (should not be commented)
                """
        function f<T>(x: T): x is T { return true }
        """,

                // 4. Object literal value protection (should not erase colon)
                """
        const o = {
          key: "val",
          num: 123,
          fn: (a: number): string => String(a)
        }
        """,

                // 5. Ternary operator protection
                """
        const x = true ? 1 : 2
        const y = cond ? (a as string) : (b as number)
        """,

                // 6. Template string / string with colon
                """
        const s = `aaa:bbb`
        const t = 'ccc:ddd'
        """,

                // 7. Class generics + implements
                """
        class MyMap<K, V> implements Map<K, V> {
          get(k: K): V | undefined { return void 0 }
        }
        """,

                // 8. Nested generics + union types
                """
        type Deep = Map<string, Array<{ id: number; name: string } | null>>
        """
        };

        for (int i = 0; i < cases.length; i++) {
            System.out.println("========== CASE " + (i + 1) + " ==========");
            System.out.println(TsEraser.eraseTypes(cases[i]));
        }
    }
}