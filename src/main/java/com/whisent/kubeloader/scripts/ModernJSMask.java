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
                    int end = i + 1;
                    boolean inClass = false;
                    while (end < n) {
                        char e = text.charAt(end);
                        if (e == '\\') {
                            end += 2;
                            continue;
                        }
                        if (e == '\n') {
                            break;
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
                        end++;
                    }
                    i = fill(out, i, Math.min(end, n));
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
}
