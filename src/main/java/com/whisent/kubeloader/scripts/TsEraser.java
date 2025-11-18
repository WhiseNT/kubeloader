package com.whisent.kubeloader.scripts;

public class TsEraser {

    public static String eraseTypes(String tsCode) {
        if (tsCode == null || tsCode.isEmpty()) {
            return tsCode;
        }

        char[] chars = tsCode.toCharArray();
        int len = chars.length;
        StringBuilder out = new StringBuilder(len);
        int i = 0;

        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inTemplate = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        int templateDepth = 0;

        boolean skippingDeclaration = false; // for interface/type only
        int braceDepth = 0;

        while (i < len) {
            char c = chars[i];

            // ========== 注释和字符串状态管理 ==========
            if (!inSingleQuote && !inDoubleQuote && !inTemplate && !inLineComment && !inBlockComment) {
                if (c == '/' && i + 1 < len) {
                    if (chars[i + 1] == '/') {
                        inLineComment = true;
                        out.append(c).append(chars[i + 1]);
                        i += 2;
                        continue;
                    } else if (chars[i + 1] == '*') {
                        inBlockComment = true;
                        out.append(c).append(chars[i + 1]);
                        i += 2;
                        continue;
                    } else if (c == 'p' || c == 'P') {
                        out.append(c);
                        i++;
                        continue;
                    }
                }
            }

            if (!inLineComment && !inBlockComment) {
                if (c == '\'' && !inDoubleQuote && !inTemplate) {
                    inSingleQuote = !inSingleQuote;
                } else if (c == '"' && !inSingleQuote && !inTemplate) {
                    inDoubleQuote = !inDoubleQuote;
                } else if (c == '`' && !inSingleQuote && !inDoubleQuote) {
                    inTemplate = !inTemplate;
                    if (inTemplate) templateDepth = 0;
                }
            }

            if (inTemplate && c == '$' && i + 1 < len && chars[i + 1] == '{') {
                templateDepth++;
                out.append(c).append(chars[i + 1]);
                i += 2;
                continue;
            }
            if (inTemplate && c == '}' && templateDepth > 0) {
                templateDepth--;
            }

            // ========== 输出注释内容 ==========
            if (inLineComment) {
                out.append(c);
                if (c == '\n' || c == '\r') {
                    inLineComment = false;
                }
                i++;
                continue;
            }
            if (inBlockComment) {
                out.append(c);
                if (c == '*' && i + 1 < len && chars[i + 1] == '/') {
                    inBlockComment = false;
                    out.append('/');
                    i += 2;
                    continue;
                }
                i++;
                continue;
            }

            // ========== 检测声明：interface / type / enum ==========
            if (!inSingleQuote && !inDoubleQuote && !inTemplate) {
                if (i == 0 || !Character.isJavaIdentifierPart(chars[i - 1])) {
                    int pos = i;

                    boolean hasModifier = false;
                    if (startsWithKeyword(chars, pos, "export")) {
                        pos += 6;
                        hasModifier = true;
                    } else if (startsWithKeyword(chars, pos, "declare")) {
                        pos += 7;
                        hasModifier = true;
                    }

                    while (pos < len && Character.isWhitespace(chars[pos])) {
                        pos++;
                    }

                    if (startsWithKeyword(chars, pos, "interface") || startsWithKeyword(chars, pos, "type")) {
                        // 记录声明起始位置
                        int declStart = pos;
                        boolean isInterface = startsWithKeyword(chars, pos, "interface");
                        String keyword = isInterface ? "interface" : "type";

                        // 跳过关键字
                        int j = pos + keyword.length();
                        while (j < len && Character.isWhitespace(chars[j])) j++;

                        // 跳过标识符（类型名）
                        if (j < len && Character.isJavaIdentifierStart(chars[j])) {
                            j++;
                            while (j < len && Character.isJavaIdentifierPart(chars[j])) j++;
                        }

                        // 跳过泛型参数（如果有）
                        if (j < len && chars[j] == '<') {
                            int depth = 1;
                            j++;
                            while (j < len && depth > 0) {
                                if (chars[j] == '<') depth++;
                                else if (chars[j] == '>') depth--;
                                else if (chars[j] == '\'' || chars[j] == '"') {
                                    char quote = chars[j];
                                    j++;
                                    while (j < len && chars[j] != quote) {
                                        if (chars[j] == '\\' && j + 1 < len) j++;
                                        j++;
                                    }
                                }
                                j++;
                            }
                        }

                        // 跳过 = 或 extends/implements（简单处理：找 { 或 ;）
                        while (j < len && chars[j] != '{' && chars[j] != ';') {
                            j++;
                        }

                        if (j >= len) {
                            // 异常情况，直接跳过
                            i = len;
                        } else if (chars[j] == ';') {
                            // type A = string; 这种形式
                            j++; // 包含分号
                            String erasedDecl = tsCode.substring(declStart, j);
                            out.append("/* [ERASED] ").append(erasedDecl).append(" */\n");
                            i = j;
                        } else if (chars[j] == '{') {
                            // interface A { ... } 或 type A = { ... }
                            int internalBraceDepth = 1;
                            j++; // 跳过 {
                            while (j < len && internalBraceDepth > 0) {
                                char ch = chars[j];
                                if (ch == '{') {
                                    internalBraceDepth++;
                                } else if (ch == '}') {
                                    internalBraceDepth--;
                                } else if (ch == '\'' || ch == '"') {
                                    char quote = ch;
                                    j++;
                                    while (j < len && chars[j] != quote) {
                                        if (chars[j] == '\\' && j + 1 < len) j++;
                                        j++;
                                    }
                                } else if (ch == '`') {
                                    // 简单处理模板字符串（不深入 ${}）
                                    j++;
                                    while (j < len && chars[j] != '`') {
                                        if (chars[j] == '\\' && j + 1 < len) j++;
                                        j++;
                                    }
                                }
                                j++;
                            }
                            // j 现在指向 } 后一位
                            String erasedDecl = tsCode.substring(declStart, j);
                            out.append("/* [ERASED] ").append(erasedDecl.replace("*/", "*\\/")).append(" */\n");
                            i = j;
                        } else {
                            i = j;
                        }
                        continue;
                    }
                    /* ========== 擦除 private/public/protected 修饰符 ========== */
                    if (!inSingleQuote && !inDoubleQuote && !inTemplate &&
                            !inLineComment && !inBlockComment) {

                        if (c == 'p' || c == 'P') { // 快速前缀
                            if (startsWithKeyword(chars, pos, "private") ||
                                    startsWithKeyword(chars, pos, "public")  ||
                                    startsWithKeyword(chars, pos, "protected")) {

                                int kwLen = 0;
                                if (startsWithKeyword(chars, pos, "private"))  kwLen = 7;
                                else if (startsWithKeyword(chars, pos, "public"))   kwLen = 6;
                                else if (startsWithKeyword(chars, pos, "protected")) kwLen = 9;

                                // 跳过整个关键字（后面已保证非标识符）
                                i = pos + kwLen;
                                continue;
                            }
                        }
                    }

                    if (startsWithKeyword(chars, pos, "enum")) {
                        i = pos + 4;
                        while (i < len && Character.isWhitespace(chars[i])) i++;
                        if (i >= len || !Character.isJavaIdentifierStart(chars[i])) {
                            out.append(new String(chars, pos, Math.min(10, len - pos)));
                            continue;
                        }

                        int nameStart = i;
                        while (i < len && Character.isJavaIdentifierPart(chars[i])) i++;
                        String enumName = new String(chars, nameStart, i - nameStart);

                        while (i < len && Character.isWhitespace(chars[i])) i++;
                        if (i >= len || chars[i] != '{') {
                            out.append(new String(chars, pos, i - pos));
                            continue;
                        }
                        i++;

                        out.append("const ").append(enumName).append(" = {};\n");

                        int nextAutoValue = 0;
                        boolean canAutoIncrement = true;

                        while (i < len) {
                            while (i < len && (Character.isWhitespace(chars[i]) ||
                                    chars[i] == ',' ||
                                    isLineBreak(chars[i]))) {
                                i++;
                            }
                            if (i >= len) break;
                            if (chars[i] == '}') {
                                i++;
                                break;
                            }

                            int idStart = i;
                            while (i < len && Character.isJavaIdentifierPart(chars[i])) i++;
                            if (idStart == i) {
                                i++;
                                continue;
                            }
                            String id = new String(chars, idStart, i - idStart);

                            boolean hasInitializer = false;
                            String initStr = null;

                            while (i < len && Character.isWhitespace(chars[i])) i++;

                            if (i < len && chars[i] == '=') {
                                hasInitializer = true;
                                i++;
                                while (i < len && Character.isWhitespace(chars[i])) i++;

                                if (i >= len) {
                                    hasInitializer = false;
                                } else {
                                    char fc = chars[i];
                                    if (fc == '-' || Character.isDigit(fc)) {
                                        int numStart = i;
                                        while (i < len && (Character.isDigit(chars[i]) || chars[i] == '.')) {
                                            i++;
                                        }
                                        initStr = new String(chars, numStart, i - numStart);
                                        try {
                                            double val = Double.parseDouble(initStr);
                                            nextAutoValue = (int) val + 1;
                                            canAutoIncrement = true;
                                        } catch (Exception e) {
                                            hasInitializer = false;
                                        }
                                    } else if (Character.isJavaIdentifierStart(fc)) {
                                        int identStart = i;
                                        while (i < len && Character.isJavaIdentifierPart(chars[i])) i++;
                                        initStr = new String(chars, identStart, i - identStart);
                                    } else {
                                        hasInitializer = false;
                                    }
                                }
                            }

                            String valueStr;
                            if (hasInitializer) {
                                valueStr = initStr;
                            } else {
                                if (canAutoIncrement) {
                                    valueStr = String.valueOf(nextAutoValue);
                                    nextAutoValue++;
                                } else {
                                    valueStr = String.valueOf(0); // fallback
                                }
                            }

                            out.append(enumName)
                                    .append("[")
                                    .append(enumName)
                                    .append(".")
                                    .append(id)
                                    .append(" = ")
                                    .append(valueStr)
                                    .append("] = \"")
                                    .append(id)
                                    .append("\";\n");
                        }

                        continue;
                    }
                }
            }


            if (skippingDeclaration) {
                if (c == '{') {
                    braceDepth++;
                } else if (c == '}') {
                    braceDepth--;
                    if (braceDepth < 0) {
                        skippingDeclaration = false;
                        i++;
                        continue;
                    }
                } else if (braceDepth == 0 && (c == ';' || isLineBreak(c))) {
                    skippingDeclaration = false;
                    if (c == ';') i++;
                    continue;
                }
                i++;
                continue;
            }


            // ========== 擦除泛型 <T> ==========
            if (!inSingleQuote && !inDoubleQuote && !inTemplate) {
                if (c == '<') {
                    boolean isGeneric = false;
                    if (i > 0) {
                        char prev = chars[i - 1];
                        if (Character.isJavaIdentifierPart(prev)) {
                            int depth = 1;
                            int j = i + 1;
                            boolean valid = true;
                            int nonWs = 0;
                            while (j < len && depth > 0) {
                                char ch = chars[j];
                                if (ch == '<') depth++;
                                else if (ch == '>') depth--;
                                else if (ch == '\'' || ch == '"') {
                                    char quote = ch;
                                    j++;
                                    while (j < len && chars[j] != quote) {
                                        if (chars[j] == '\\' && j + 1 < len) j++;
                                        j++;
                                    }
                                } else if (!Character.isWhitespace(ch)) {
                                    nonWs++;
                                    if (!(Character.isJavaIdentifierPart(ch) ||
                                            ch == ',' || ch == '.' || ch == '?' ||
                                            ch == '|' || ch == '&' || ch == '(' ||
                                            ch == ')' || ch == '[' || ch == ']' ||
                                            ch == '{' || ch == '}')) {
                                        valid = false;
                                        break;
                                    }
                                }
                                j++;
                            }

                            if (valid && depth == 0 && nonWs > 0 && j <= len) {
                                char after = (j < len) ? chars[j] : '\0';
                                if (after == '(' || after == '{' ||
                                        Character.isWhitespace(after) || after == '\n' || after == '\r' || after == '\0') {
                                    isGeneric = true;
                                }
                            }
                        }
                    }


                    if (isGeneric) {
                        int depth = 1;
                        int j = i + 1;
                        while (j < len && depth > 0) {
                            char ch = chars[j];
                            if (ch == '<') depth++;
                            else if (ch == '>') depth--;
                            else if (ch == '\'' || ch == '"') {
                                char quote = ch;
                                j++;
                                while (j < len && chars[j] != quote) {
                                    if (chars[j] == '\\' && j + 1 < len) j++;
                                    j++;
                                }
                            }
                            j++;
                        }
                        if (depth == 0) {
                            i = j;
                            continue;
                        }

                    }

                }
            }
            // ========== 擦除箭头函数（形参 + 返回类型） ==========
            if (!inSingleQuote && !inDoubleQuote && !inTemplate &&
                    !inLineComment && !inBlockComment &&
                    c == '>' && i > 0 && chars[i-1] == '=') {

                // 1. 向前找匹配的 )
                int paren = i - 2;
                int depth = 0;
                while (paren >= 0) {
                    char ch = chars[paren];
                    if (ch == ')') depth++;
                    else if (ch == '(') depth--;
                    if (depth == 0) break;
                    paren--;
                }
                if (paren < 0) { out.append(c); i++; continue; }

                // 2. 清形参类型：从缓冲区里删掉 ":Type"
                int paramStart = out.length() - (i - 1 - paren); // '(' 在 out 里的位置
                int deleteFrom = -1;
                for (int k = paren + 1; k < i - 1; k++) {
                    if (chars[k] == ':') deleteFrom = k;
                    else if (deleteFrom != -1 && (chars[k] == ',' || chars[k] == ')')) {
                        int offsetInOut = paramStart + (deleteFrom - paren);
                        out.delete(offsetInOut, paramStart + (k - paren));
                        deleteFrom = -1;
                    }
                }
                if (deleteFrom != -1) { // 末尾参数
                    int offsetInOut = paramStart + (deleteFrom - paren);
                    out.delete(offsetInOut, paramStart + (i - 1 - paren));
                }

                // 3. 清返回类型：=> 后第一个冒号
                int colon = i + 1;
                while (colon < len && Character.isWhitespace(chars[colon])) colon++;
                if (colon < len && chars[colon] == ':') {
                    int typeEnd = colon + 1;
                    while (typeEnd < len && !isValueStart(chars[typeEnd])) typeEnd++;
                    i = typeEnd;
                    continue;
                }
                // 无返回类型
                out.append(c);
                i++;
                continue;
            }


            // ========== 擦除类型注解 : Type ==========
            if (!inSingleQuote && !inDoubleQuote && !inTemplate) {
                if (c == ':') {
                    // 新增：检查是否在函数返回类型位置
                    boolean isFunctionReturnType = false;
                    int lookBack = i - 1;
                    while (lookBack >= 0 && Character.isWhitespace(chars[lookBack])) {
                        lookBack--;
                    }
                    if (lookBack >= 0 && chars[lookBack] == ')') {
                        int lookAhead = i + 1;
                        while (lookAhead < len && Character.isWhitespace(chars[lookAhead])) {
                            lookAhead++;
                        }
                        if (lookAhead < len && chars[lookAhead] == '{') {
                            isFunctionReturnType = true;
                        }
                    }

                    if (isFunctionReturnType) {
                        int j = i + 1;
                        while (j < len && Character.isWhitespace(chars[j])) j++;
                        i = j; // will output '{' in default case
                        continue;
                    }

                    if (isLikelyRuntimeValueAfterColon(chars, i + 1)) {
                        out.append(c);
                        i++;
                        continue;
                    }

                    int j = i + 1;
                    while (j < len && Character.isWhitespace(chars[j])) j++;

                    int angleDepth = 0;
                    while (j < len) {
                        char ch = chars[j];
                        if (ch == '<') {
                            angleDepth++;
                        } else if (ch == '>') {
                            if (angleDepth > 0) {
                                angleDepth--;
                            } else {
                                break;
                            }
                        } else if (angleDepth == 0) {
                            if (ch == ',' || ch == ';' || ch == '=' || ch == ')' || ch == ']' || ch == '}' ||
                                    ch == '\n' || ch == '\r' || ch == '{') {
                                break;
                            }
                        }

                        if ((ch == '\'' || ch == '"') && angleDepth > 0) {
                            char quote = ch;
                            j++;
                            while (j < len && chars[j] != quote) {
                                if (chars[j] == '\\' && j + 1 < len) j++;
                                j++;
                            }
                        }

                        j++;
                    }

                    i = j;
                    continue;
                }

            }

            // ========== 默认输出 ==========
            out.append(c);
            i++;
        }

        return out.toString();
    }

    private static boolean isLineBreak(char c) {
        return c == '\n' || c == '\r';
    }

    private static boolean startsWithKeyword(char[] chars, int pos, String keyword) {
        if (pos + keyword.length() > chars.length) return false;
        for (int i = 0; i < keyword.length(); i++) {
            if (chars[pos + i] != keyword.charAt(i)) return false;
        }
        if (pos + keyword.length() < chars.length) {
            char next = chars[pos + keyword.length()];
            return !Character.isJavaIdentifierPart(next);
        }
        return true;
    }

    private static boolean isLikelyRuntimeValueAfterColon(char[] chars, int pos) {
        if (pos >= chars.length) return false;
        int p = pos;
        while (p < chars.length && Character.isWhitespace(chars[p])) p++;

        if (p >= chars.length) return false;
        char c = chars[p];

        // 1. 字面量
        if (c == '"' || c == '\'' || c == '`' || c == '{' || c == '[' || c == '/' || c == '(') {
            return true;
        }
        if (Character.isDigit(c)) return true;
        if (p + 4 <= chars.length && match(chars, p, "true") && !isIdentifierContinuation(chars, p + 4)) return true;
        if (p + 5 <= chars.length && match(chars, p, "false") && !isIdentifierContinuation(chars, p + 5)) return true;
        if (p + 4 <= chars.length && match(chars, p, "null") && !isIdentifierContinuation(chars, p + 4)) return true;
        if (p + 3 <= chars.length && match(chars, p, "NaN")) return true;
        if (p + 8 <= chars.length && match(chars, p, "Infinity")) return true;

        // 2. 新增：标识符（变量名、函数名、enum 成员 …）
        if (Character.isJavaIdentifierStart(c)) {
            return true;
        }

        return false;
    }

    private static boolean match(char[] chars, int start, String s) {
        for (int i = 0; i < s.length(); i++) {
            if (start + i >= chars.length || chars[start + i] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIdentifierContinuation(char[] chars, int pos) {
        return pos < chars.length && Character.isJavaIdentifierPart(chars[pos]);
    }
    /* --------------- 下面是两个小工具 --------------- */
    /* 辅助：JS 值可能开头的字符 */
    private static boolean isValueStart(char ch) {
        return ch == '{' || ch == '"' || ch == '\'' || ch == '`' ||
                Character.isJavaIdentifierStart(ch) || Character.isDigit(ch) ||
                ch == '(' || ch == '[' || ch == '/' || ch == '?';
    }

    public static void main(String[] args) {
        String tsCode =
        """
const f = (n:number):boolean => n>0 ? "pos" : "neg";
                """;
        String jsCode = TsEraser.eraseTypes(tsCode);
        System.out.println(jsCode);
    }
}