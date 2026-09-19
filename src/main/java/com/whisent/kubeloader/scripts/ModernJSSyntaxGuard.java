package com.whisent.kubeloader.scripts;

/**
 * 转换前的安全网：把 Rhino <b>静默算错</b> 的语法变成显式失败。
 *
 * <p>背景：Rhino 能解析一部分现代语法，却算错它的值。最典型的是空值合并
 * {@code a ?? b} —— 右侧表达式会被完全忽略（{@code null ?? 7} 得到 0 而不是 7），
 * 既不报语法错误也不抛异常。这类「不报错但结果错」比语法错误危险得多，
 * 所以这里在转换之前先扫一遍。</p>
 *
 * <p>扫描只看真正的代码部分：字符串、注释、正则字面量会被跳过；
 * 模板字符串里的 {@code ${...}} 属于代码，会递归检查（其中嵌套的模板串也一并处理）。</p>
 *
 * <p>已知局限：{@code /} 是正则还是除号，靠「上一个有意义字符」判断，
 * 因此 {@code return /a??/} 这种「关键字后面紧跟正则」的写法可能被误判为正则之外，
 * 从而产生误报。这属于低概率写法，暂时接受。</p>
 */
public final class ModernJSSyntaxGuard {

    private ModernJSSyntaxGuard() {
    }

    /** 发现不该放过去的语法时抛出 {@link ModernJSParseException}。 */
    public static void check(String source) {
        if (source == null || source.isEmpty()) return;
        new Scanner(source).scanAll();
    }

    /** 单趟扫描，只关心代码部分。 */
    private static final class Scanner {

        private final String s;
        private final int n;
        private int i;
        private int line = 1;
        /** 上一个有意义的字符，用于判断 '/' 是正则还是除号 */
        private char prev = '\0';

        Scanner(String s) {
            this.s = s;
            this.n = s.length();
        }

        void scanAll() {
            while (i < n) {
                char c = s.charAt(i);

                if (c == '\n') {
                    line++;
                    i++;
                    continue;
                }
                if (c == '"' || c == '\'') {
                    skipString(c);
                    prev = c;
                    continue;
                }
                if (c == '`') {
                    skipTemplate();
                    prev = '`';
                    continue;
                }
                if (c == '/' && i + 1 < n) {
                    char d = s.charAt(i + 1);
                    if (d == '/') {
                        skipLineComment();
                        continue;
                    }
                    if (d == '*') {
                        skipBlockComment();
                        continue;
                    }
                    if (canStartRegex(prev)) {
                        skipRegex();
                        prev = ')';
                        continue;
                    }
                }

                checkNullish();
                if (!Character.isWhitespace(c)) {
                    prev = c;
                }
                i++;
            }
        }

        // ---- 检查点 ----

        private void checkNullish() {
            if (s.charAt(i) == '?' && i + 1 < n && s.charAt(i + 1) == '?') {
                throw new ModernJSParseException(
                        "检测到 `??`（空值合并），但当前 Rhino 引擎会算错它的值：右侧表达式会被忽略",
                        line, lineText(),
                        "改写为显式判断，例如 `a !== null && a !== undefined ? a : b`；"
                                + "若 a 有副作用（函数调用等），先存进临时变量再判断");
            }
        }

        // ---- 跳过各类字面量与注释 ----

        private void skipString(char quote) {
            i++;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == quote) {
                    i++;
                    return;
                }
                if (c == '\n') line++;
                i++;
            }
        }

        private void skipLineComment() {
            while (i < n && s.charAt(i) != '\n') i++;
        }

        private void skipBlockComment() {
            i += 2;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\n') line++;
                if (c == '*' && i + 1 < n && s.charAt(i + 1) == '/') {
                    i += 2;
                    return;
                }
                i++;
            }
        }

        /** 跳过模板串；${...} 里是代码，递归进去继续检查。 */
        private void skipTemplate() {
            i++; // 反引号
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == '\n') {
                    line++;
                    i++;
                    continue;
                }
                if (c == '`') {
                    i++;
                    return;
                }
                if (c == '$' && i + 1 < n && s.charAt(i + 1) == '{') {
                    i += 2;
                    scanInterpolation();
                    continue;
                }
                i++;
            }
        }

        /** 扫描 ${...} 内部：入口 i 在 '{' 之后，出口 i 在匹配 '}' 之后。 */
        private void scanInterpolation() {
            int depth = 1;
            while (i < n && depth > 0) {
                char c = s.charAt(i);
                if (c == '\n') {
                    line++;
                    i++;
                    continue;
                }
                if (c == '"' || c == '\'') {
                    skipString(c);
                    prev = c;
                    continue;
                }
                if (c == '`') {
                    skipTemplate();
                    prev = '`';
                    continue;
                }
                if (c == '/' && i + 1 < n) {
                    char d = s.charAt(i + 1);
                    if (d == '/') {
                        skipLineComment();
                        continue;
                    }
                    if (d == '*') {
                        skipBlockComment();
                        continue;
                    }
                    if (canStartRegex(prev)) {
                        skipRegex();
                        prev = ')';
                        continue;
                    }
                }

                checkNullish();
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
                if (!Character.isWhitespace(c)) {
                    prev = c;
                }
                i++;
            }
        }

        /** 正则字面量：未转义的 '/' 结束（字符类 [..] 里的 '/' 不算），随后跳过 flags。 */
        private void skipRegex() {
            i++;
            boolean inClass = false;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == '\n') return; // 换行说明这不是正则，放弃
                if (c == '[') {
                    inClass = true;
                } else if (c == ']') {
                    inClass = false;
                } else if (c == '/' && !inClass) {
                    i++;
                    break;
                }
                i++;
            }
            while (i < n && Character.isLetter(s.charAt(i))) i++;
        }

        /** 上一个字符若可能出现在表达式末尾，则 '/' 是除号；否则按正则处理。 */
        private boolean canStartRegex(char p) {
            if (p == '\0') return true;
            return !(Character.isLetterOrDigit(p) || p == '_' || p == '$'
                    || p == ')' || p == ']' || p == '}' || p == '"' || p == '\'' || p == '`');
        }

        /** 当前行原文（去首尾空白），用于报错定位。 */
        private String lineText() {
            int start = s.lastIndexOf('\n', Math.max(0, i - 1)) + 1;
            int end = s.indexOf('\n', i);
            if (end == -1) end = n;
            if (start > end) start = end;
            return s.substring(start, end).trim();
        }
    }
}
