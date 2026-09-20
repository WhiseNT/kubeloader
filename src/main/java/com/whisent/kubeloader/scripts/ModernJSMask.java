package com.whisent.kubeloader.scripts;

import java.util.ArrayList;
import java.util.List;

/**
 * 掩码与结构工具：给「转换前的结构判断」提供两样东西。
 *
 * <p>一是掩码：把字符串、注释、正则、模板串替换成同长度的占位字符。做结构判断前
 * 必须先掩码，否则它们里面的括号、逗号和 {@code ...} 会被当成真正的代码，改出错误结果
 * （例如 {@code let s = "...";} 或 {@code /\(/} 会把一片无辜的代码卷进判断）。</p>
 *
 * <p>二是两个基于掩码文本的小工具：{@link #matchClose} 找配对的闭合括号，
 * {@link #splitTopLevel} 按顶层逗号切分参数/成员。</p>
 *
 * <p>掩码<b>只用于判断</b>：真正取内容时要回到原文——因为掩码与原文字符数完全一致，
 * 下标可以直接复用。</p>
 *
 * <p>已知局限：{@code /} 是正则还是除号靠「上一个有意义字符」猜（见
 * {@link #canStartRegex}），所以 {@code return /a/} 这类「关键字后紧跟正则」的写法
 * 可能被误判。误判会导致掩掉一片本应检查的代码，也就是<b>漏掉</b>转换（而不是改错），
 * 属于可接受的方向。</p>
 */
final class ModernJSMask {

    /** 占位字符：正常源码里不会出现，也不会跟别的字符拼出 {@code ...} 这类写法 */
    static final char MASK = '\u0001';

    private ModernJSMask() {
    }

    /** 返回同长度的掩码文本。 */
    static String mask(String text) {
        char[] out = text.toCharArray();
        int n = out.length;
        char prev = '\0';
        int i = 0;

        while (i < n) {
            char c = text.charAt(i);

            if (c == '"' || c == '\'') {
                int end = i + 1;
                while (end < n) {
                    char d = text.charAt(end);
                    if (d == '\\') {
                        end += 2;
                        continue;
                    }
                    if (d == c || d == '\n') {
                        break;
                    }
                    end++;
                }
                i = fill(out, i, Math.min(end + 1, n));
                prev = c;
                continue;
            }

            if (c == '`') {
                int end = i + 1;
                while (end < n) {
                    char d = text.charAt(end);
                    if (d == '\\') {
                        end += 2;
                        continue;
                    }
                    if (d == '`') {
                        break;
                    }
                    end++;
                }
                i = fill(out, i, Math.min(end + 1, n));
                prev = '`';
                continue;
            }

            if (c == '/' && i + 1 < n) {
                char d = text.charAt(i + 1);
                if (d == '/') {
                    int end = i;
                    while (end < n && text.charAt(end) != '\n') {
                        end++;
                    }
                    i = fill(out, i, end);
                    continue;
                }
                if (d == '*') {
                    int end = i + 2;
                    while (end < n && !(text.charAt(end) == '*' && end + 1 < n && text.charAt(end + 1) == '/')) {
                        end++;
                    }
                    i = fill(out, i, Math.min(end + 2, n));
                    continue;
                }
                if (canStartRegex(prev)) {
                    i = fill(out, i, Math.min(skipRegexLiteral(text, i) + 1, n));
                    prev = ')';
                    continue;
                }
            }

            if (!Character.isWhitespace(c)) {
                prev = c;
            }
            i++;
        }

        return new String(out);
    }

    private static int fill(char[] out, int from, int to) {
        for (int k = from; k < to; k++) {
            out[k] = MASK;
        }
        return to;
    }

    /** '/' 前面是这个字符时，'/' 更可能是除号而不是正则开头。 */
    static boolean canStartRegex(char p) {
        if (p == '\0') {
            return true;
        }
        return !(Character.isLetterOrDigit(p) || p == '_' || p == '$'
                || p == ')' || p == ']' || p == '}' || p == '"' || p == '\'' || p == '`');
    }

    /**
     * 去掉**首尾**的空白与注释（中间的注释保留）。
     *
     * <p>类成员分类只看「代码形状」：{@code m() { return 1 } // 这是个方法} 这种写法里，
     * 行尾注释会让 {@code endsWith("}")} 失手，成员就被静默丢掉了。
     * 首部注释同理（{@code /* c *&#47; get x() {...}} 会让 {@code startsWith("get")} 失手）。</p>
     */
    static String trimComments(String text) {
        String s = text;
        for (int round = 0; round < 64; round++) {
            String t = s.trim();
            if (t.startsWith("//")) {
                int nl = t.indexOf('\n');
                s = nl < 0 ? "" : t.substring(nl + 1);
                continue;
            }
            if (t.startsWith("/*")) {
                int end = t.indexOf("*/");
                s = end < 0 ? "" : t.substring(end + 2);
                continue;
            }
            String tail = stripTrailingComment(t);
            if (!tail.equals(t)) {
                s = tail;
                continue;
            }
            return t;
        }
        return s.trim();
    }

    /**
     * 跳过从 {@code from} 开始的空白与注释，返回第一个真正「代码」字符的下标。
     *
     * <p>很多地方要按「代码形状」判断一段源码是什么东西（这一段是展开项吗？是构造函数吗？
     * 是模式吗？）。只看首字符或只跳空白的话，段首多一条注释就会判错 —— 而注释是合法的、
     * 而且很常见的写法。判错的后果通常是「生成代码时把这段原样塞进输出」，
     * 于是结果里留着 Rhino 解析不了的现代语法。</p>
     */
    static int skipTrivia(String text, int from) {
        int i = from;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                int nl = text.indexOf('\n', i);
                i = nl < 0 ? text.length() : nl + 1;
                continue;
            }
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                i = end < 0 ? text.length() : end + 2;
                continue;
            }
            return i;
        }
        return i;
    }

    /**
     * {@code [from, end)} 这段文本里「代码」的结束位置：末尾的行注释 / 块注释不算代码。
     *
     * <p>为什么要这个：掩码里的注释是一串 {@link #MASK} 字符，既不是 {@code ;} 也不是
     * 换行，所以扫描「右值到哪结束」时会直接穿过注释停在行尾。于是右值被当成
     * {@code empty // 变形注释} 这种带注释的整串，生成的新代码被拼到了 {@code //} 后面，
     * 整段被注释吞掉——静默算错。生成替换代码前必须先把右值收在真正的代码末尾。</p>
     */
    static int codeEndIn(String text, int from, int end) {
        if (end > text.length()) {
            end = text.length();
        }
        if (from >= end) {
            return from;
        }
        return from + stripTrailingComment(text.substring(from, end)).length();
    }

    /**
     * 文本末尾是不是停在行注释里（最后一行里出现过真注释 {@code //}）。
     *
     * <p>往这种文本后面拼生成代码，拼出来的东西会被那条注释整段吞掉：
     * {@code return this._c // 注释} 后面接 {@code },} 就变成
     * {@code return this._c // 注释  },}，收尾的括号没了。拼接前必须先换行。</p>
     */
    static boolean endsInLineComment(String text) {
        int lastLineStart = text.lastIndexOf('\n') + 1;
        return findLineComment(text.substring(lastLineStart)) >= 0;
    }

    /** 去掉末尾的注释（最后一行是行注释，或整段以块注释收尾）。 */
    private static String stripTrailingComment(String text) {        if (text.endsWith("*/")) {
            int start = text.lastIndexOf("/*");
            if (start >= 0) {
                return text.substring(0, start);
            }
        }
        int lastLineStart = text.lastIndexOf('\n') + 1;
        int comment = findLineComment(text.substring(lastLineStart));
        if (comment >= 0) {
            return text.substring(0, lastLineStart + comment);
        }
        return text;
    }

    /** 在一行里找行注释的起始下标（跳过引号里的内容）；没有返回 -1。 */
    private static int findLineComment(String line) {
        char quote = '\0';
        for (int i = 0; i + 1 < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != '\0') {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = '\0';
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
            } else if (c == '/' && line.charAt(i + 1) == '/') {
                return i;
            }
        }
        return -1;
    }

    /**
     * 位置 {@code i} 处的 {@code /} 能不能开始一个正则字面量。
     *
     * <p>比 {@link #canStartRegex(char)} 多看两件事：往前跳过空白（{@code a = / x /} 里
     * 紧邻的字符是空格）；以及认关键字（{@code return /}/}、{@code typeof /x/} —— 关键字
     * 后面是允许正则的，但前一个字符是字母，朴素判断会算成除号）。</p>
     */
    static boolean canStartRegexAt(String text, int i) {
        // `/*` 与 `//` 永远不是合法正则字面量的开头：前者的 body 以量词 `*` 起头（没东西可重复），
        // 后者是空 body，两者都是语法错。所以必须先把注释排除掉。
        // 漏了这一步的后果很实在：`/* c */ class A {` 开头的块注释会被当成正则字面量跳过，
        // 于是后面 `class` 的「前一个有效字符」变成 `/`，整个类不再被判成语句位置、
        // 原样留在转换结果里 → Rhino 报 identifier is a reserved word: class。
        if (i + 1 < text.length()) {
            char next = text.charAt(i + 1);
            if (next == '*' || next == '/') {
                return false;
            }
        }
        int j = i - 1;
        while (j >= 0 && Character.isWhitespace(text.charAt(j))) {
            j--;
        }
        if (j < 0) {
            return true;
        }
        char prev = text.charAt(j);
        if (Character.isLetterOrDigit(prev) || prev == '_' || prev == '$') {
            int w = j + 1;
            while (w > 0 && isIdentPart(text.charAt(w - 1))) {
                w--;
            }
            switch (text.substring(w, j + 1)) {
                case "return":
                case "typeof":
                case "instanceof":
                case "in":
                case "of":
                case "case":
                case "delete":
                case "void":
                case "do":
                case "else":
                case "yield":
                case "await":
                case "new":
                    return true;
                default:
                    return false;
            }
        }
        return canStartRegex(prev);
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /**
     * 从 {@code from} 处的 {@code /} 开始跳过整个正则字面量（含字符组与修饰符），
     * 返回**最后一个字符**的下标（调用方接着 {@code i++} 即可）。
     *
     * <p>必须优先于注释判断：{@code /[/*]/}、{@code /https?:\/\//} 里的 {@code /*} 与
     * {@code //} 都不是注释开头；判错会把后面整段代码当成注释吞掉，
     * 结果连后面的 class 都找不到（报「找不到配对的 }」或原样留着 class）。</p>
     */
    static int skipRegexLiteral(String text, int from) {
        int n = text.length();
        int end = from + 1;
        boolean inClass = false;
        while (end < n) {
            char e = text.charAt(end);
            if (e == '\\') {
                end += 2;
                continue;
            }
            if (e == '\n') {
                break; // 正则不能跨行，说明这不是正则
            }
            if (e == '[') {
                inClass = true;
            } else if (e == ']') {
                inClass = false;
            } else if (e == '/' && !inClass) {
                end++;
                break;
            }
            end++;
        }
        while (end < n && Character.isLetter(text.charAt(end))) {
            end++; // 修饰符 gimsuy
        }
        return Math.min(end, n) - 1;
    }

    /** 与 open 处括号配对的闭合括号下标；找不到返回 -1。 */
    static int matchClose(String masked, int open) {
        char opener = masked.charAt(open);
        char closer = opener == '(' ? ')' : (opener == '[' ? ']' : '}');
        int depth = 0;
        for (int i = open; i < masked.length(); i++) {
            char c = masked.charAt(i);
            if (c == opener) {
                depth++;
            } else if (c == closer) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** 按顶层逗号切分 [from, to)，返回每段的 [start, end) 下标（原文/掩码同下标）。 */
    static List<int[]> splitTopLevel(String masked, int from, int to) {
        List<int[]> parts = new ArrayList<>();
        int depth = 0;
        int start = from;
        for (int i = from; i < to; i++) {
            char c = masked.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(new int[]{start, i});
                start = i + 1;
            }
        }
        parts.add(new int[]{start, to});
        return parts;
    }

    /**
     * 位置 {@code i} 上的 {@code =} 是不是「真正的赋值等号」
     * （不是 {@code ==} / {@code ===} / {@code =>} / 复合赋值运算符的一部分）。
     */
    static boolean isAssignEquals(String masked, int i) {
        char prev = i > 0 ? masked.charAt(i - 1) : '\0';
        char next = i + 1 < masked.length() ? masked.charAt(i + 1) : '\0';
        if (next == '=' || next == '>') {
            return false;
        }
        return prev != '=' && prev != '!' && prev != '<' && prev != '>' && prev != '+'
                && prev != '-' && prev != '*' && prev != '/' && prev != '%' && prev != '&'
                && prev != '|' && prev != '^';
    }

    /**
     * [from, to) 里第一个「顶层的真正赋值等号」的下标（括号 / 方括号 / 花括号深度为 0）；
     * 没有返回 -1。
     *
     * <p>用来区分「字段赋值」和「方法头」：{@code BASE = Math.max(1, 2)} 有顶层 {@code =}，
     * 而 {@code get x()} / {@code m(a = 1)} 没有（默认参数的 {@code =} 在括号里）。</p>
     */
    static int topLevelAssign(String masked, int from, int to) {
        int depth = 0;
        for (int i = from; i < to; i++) {
            char c = masked.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == '=' && depth == 0 && isAssignEquals(masked, i)) {
                return i;
            }
        }
        return -1;
    }
}
