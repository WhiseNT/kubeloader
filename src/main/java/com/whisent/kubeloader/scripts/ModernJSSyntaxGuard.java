package com.whisent.kubeloader.scripts;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Rhino 兼容性预检。两类问题分开处理：
 *
 * <ul>
 *   <li><b>静默算错</b>（{@link #check}）：Rhino 能解析却算错值的语法。最典型的是空值合并
 *       {@code a ?? b} —— 左侧为 null/undefined 时右侧表达式被整个忽略
 *       （{@code null ?? 7} 得到 0 而不是 7），既不报语法错误也不抛异常。
 *       「不报错但结果错」比语法错误危险得多，所以这类必须硬失败。
 *       注意这条只在目标引擎含 Rhino 时才成立，GraalJS 下 {@code ??} 是正常的。</li>
 *   <li><b>缺失的内建 / 解析不了的语法</b>（{@link #collectWarnings}、
 *       {@link #explainUnsupported}）：Rhino 里根本没有的东西。它们迟早会报错，
 *       但报错本身看不出该怎么改（{@code Cannot find function at in object ...}），
 *       所以这里补一句可执行的建议。这类只提示不拦截——同样的代码可能写在
 *       永远不会执行到的分支里，硬失败反而会误伤。</li>
 * </ul>
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

    /**
     * Rhino 缺失的内建：{显示名, 源码里要找的字面量, 改写建议}。
     *
     * <p>字面量以 {@code .} 开头的（如 {@code .at(}）天然带接收者，只按原样匹配；
     * 其余只在「不是属性名的一部分」时才算命中，避免把 {@code foo.Promise}
     * 这种自定义属性误报成内建。名单来自对生产 Rhino
     * （rhino-416294-7104526）的实测探针。</p>
     */
    private static final String[][] MISSING_BUILTINS = {
            {"Promise", "Promise",
                    "Rhino 没有 Promise；改用回调，或给这个脚本单独加 `//engine: graaljs`"},
            {"Proxy", "Proxy",
                    "Rhino 没有 Proxy；换用普通对象/事件，或给这个脚本单独加 `//engine: graaljs`"},
            {"Reflect", "Reflect",
                    "Rhino 没有 Reflect；直接用属性访问，或改用 `//engine: graaljs`"},
            {"globalThis", "globalThis",
                    "Rhino 没有 globalThis；用 this 或直接写全局名（如 Math）"},
            {"structuredClone", "structuredClone",
                    "改用 JSON.parse(JSON.stringify(x)) 做深拷贝"},
            {"Array/String.prototype.at", ".at(",
                    "`.at(-1)` 改用 `x[x.length - 1]`（字符串同理）；若是自己定义的同名方法可忽略"},
            {"Object.hasOwn", "Object.hasOwn",
                    "改用 Object.prototype.hasOwnProperty.call(obj, key)"},
            {"Object.fromEntries", "Object.fromEntries",
                    "改用循环自己往对象里塞键值"},
            {"String.prototype.replaceAll", ".replaceAll(",
                    "改用 str.split(a).join(b)"},
            {"Array.prototype.flat", ".flat(",
                    "手动展平一层，例如 [].concat(...arr)；若是自己定义的同名方法可忽略"},
            {"Array.prototype.flatMap", ".flatMap(",
                    "改用 map 之后再展平；若是自己定义的同名方法可忽略"},
            {"String.prototype.matchAll", ".matchAll(",
                    "改用正则的 exec 配合 while 循环"},
    };

    /** 发现不该放过去的语法时抛出 {@link ModernJSParseException}。 */
    public static void check(String source) {
        if (source == null || source.isEmpty()) return;
        new Scanner(source, false).scanAll();
    }

    /**
     * 扫一遍代码部分，返回「用到了 Rhino 没有的内建」的提示（可能为空）。
     * 只在目标引擎含 Rhino 时调用有意义。
     */
    public static List<String> collectWarnings(String source) {
        if (source == null || source.isEmpty()) return List.of();
        Scanner scanner = new Scanner(source, true);
        scanner.scanAll();
        return scanner.warnings;
    }

    /**
     * 针对「Rhino 解析不了、报错又难懂」的语法给出改写建议；没有命中返回 {@code null}。
     * 用于在 Rhino 报错之后补一句人话，不影响是否放行。
     */
    public static String explainUnsupported(String source) {
        if (source == null || source.isEmpty()) return null;
        Scanner scanner = new Scanner(source, true);
        scanner.scanAll();
        if (scanner.unsupportedHints.isEmpty()) return null;
        return String.join("；", scanner.unsupportedHints);
    }

    /** 单趟扫描，只关心代码部分。 */
    private static final class Scanner {

        private final String s;
        private final int n;
        /** true：只收集提示（缺失内建 + 解析不了的语法），不抛异常 */
        private final boolean hintOnly;
        private int i;
        private int line = 1;
        /** 上一个有意义的字符，用于判断 '/' 是正则还是除号 */
        private char prev = '\0';
        /** hintOnly 模式下的产出：缺失内建提示 */
        private final List<String> warnings = new ArrayList<>();
        /** 已提示过的内建名，避免同一个名字刷屏 */
        private final Set<String> warnedBuiltins = new HashSet<>();
        /** hintOnly 模式下的产出：解析不了的语法改写建议 */
        private final Set<String> unsupportedHints = new LinkedHashSet<>();

        Scanner(String s, boolean hintOnly) {
            this.s = s;
            this.n = s.length();
            this.hintOnly = hintOnly;
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

                checkCode();
                if (!Character.isWhitespace(c)) {
                    prev = c;
                }
                i++;
            }
        }

        // ---- 检查点 ----

        /** 一个字符位置上要做的全部检查。两种模式互斥：要么硬失败，要么只收集提示。 */
        private void checkCode() {
            if (hintOnly) {
                checkMissingBuiltins();
                checkUnsupportedSyntax();
            } else {
                checkNullish();
            }
        }

        private void checkNullish() {
            if (s.charAt(i) == '?' && i + 1 < n && s.charAt(i + 1) == '?') {
                throw new ModernJSParseException(
                        "检测到 `??`（空值合并），但当前 Rhino 引擎会算错它的值：右侧表达式会被忽略",
                        line, lineText(),
                        "改写为显式判断，例如 `a !== null && a !== undefined ? a : b`；"
                                + "若 a 有副作用（函数调用等），先存进临时变量再判断");
            }
        }

        /**
         * 缺失内建：Rhino 里没有这个名字/方法，跑到那一行就会抛 ReferenceError/TypeError。
         * 同名只提示一次（同一个脚本里 Promise 往往出现很多次）。
         */
        private void checkMissingBuiltins() {
            for (String[] def : MISSING_BUILTINS) {
                String name = def[0];
                if (warnedBuiltins.contains(name)) continue;
                if (!matchesCode(def[1])) continue;
                warnedBuiltins.add(name);
                warnings.add("第 " + line + " 行用到了 " + name + "：" + def[2]);
            }
        }

        /**
         * 命中一段「代码里的字面量」。
         *
         * <p>前面不能是标识符或 {@code .}（否则是更长名字或属性名的一部分，
         * 如 {@code Promises}、{@code foo.Promise}）；以 {@code .} 开头的字面量
         * 自带接收者，不再检查前一个字符（否则会把 {@code arr.at(} 误杀）。
         * 后面不能紧跟标识符字符。</p>
         */
        private boolean matchesCode(String needle) {
            if (!s.startsWith(needle, i)) return false;
            if (!needle.startsWith(".") && i > 0 && isIdentPart(s.charAt(i - 1))) {
                return false;
            }
            if (i > 0 && s.charAt(i - 1) == '.') return false;
            int after = i + needle.length();
            if (after < n && isIdentPart(needle.charAt(needle.length() - 1))
                    && isIdentPart(s.charAt(after))) {
                return false;
            }
            return true;
        }

        private boolean isIdentPart(char c) {
            return Character.isLetterOrDigit(c) || c == '_' || c == '$';
        }

        /**
         * Rhino 解析不了的语法：记下改写建议。
         * 只用于给报错补一句人话（实际仍由 Rhino 的 parser 判定失败），
         * 所以这里宁可多提示几种，也不追求精确。
         */
        private void checkUnsupportedSyntax() {
            if (matchesCode("async") || matchesCode("await")) {
                addUnsupported("async/await",
                        "Rhino 不支持；改用回调/事件，或给这个脚本单独加 `//engine: graaljs`");
            }
            if (s.startsWith("...", i)) {
                addUnsupported("`...`（展开/剩余参数）", "Rhino 不支持；用 apply/concat/arguments 改写");
            }
            if (s.startsWith("||=", i) || s.startsWith("&&=", i) || s.startsWith("??=", i)) {
                addUnsupported("逻辑赋值（||= / &&= / ??=）", "改写为 a = a || b 这种完整赋值");
            }
            if (s.startsWith("?.(", i)) {
                addUnsupported("可选调用 f?.()", "改写为 f && f()");
            }
            if (s.startsWith("new.target", i)) {
                addUnsupported("new.target", "换一种写法判断是否被 new 调用");
            }
            if (s.charAt(i) == '#' && i + 1 < n && isIdentPart(s.charAt(i + 1))) {
                addUnsupported("私有字段 #x", "改写为普通属性（约定以下划线开头）");
            }
            if (s.charAt(i) == '_' && i > 0 && i + 1 < n
                    && Character.isDigit(s.charAt(i - 1)) && Character.isDigit(s.charAt(i + 1))) {
                addUnsupported("数字分隔符 1_000", "去掉下划线");
            }
            if (s.charAt(i) == 'n' && i > 0 && Character.isDigit(s.charAt(i - 1))
                    && (i + 1 >= n || !isIdentPart(s.charAt(i + 1)))) {
                addUnsupported("BigInt 字面量 1n", "Rhino 没有 BigInt，改用普通数字");
            }
        }

        private void addUnsupported(String what, String advice) {
            for (String existing : unsupportedHints) {
                if (existing.contains(what)) return;
            }
            if (unsupportedHints.size() >= 3) return; // 最多三条，避免刷屏
            unsupportedHints.add("检测到 " + what + "，Rhino 不支持：" + advice);
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

                checkCode();
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
