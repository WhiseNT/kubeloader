package com.whisent.kubeloader.scripts;

/**
 * 一批高频「语法糖」的降级：Rhino 解析不了这几种写法，而后面的引擎能力补不回来。
 *
 * <ul>
 *   <li>数字分隔符：{@code 1_000} → {@code 1000}（只在数字之间的下划线才动）</li>
 *   <li>可选 catch 绑定：{@code catch {}} → {@code catch (__kl_catch) {}}</li>
 *   <li>逻辑赋值：{@code a ||= b} → {@code a || (a = b)}；
 *       {@code a &&= b} → {@code a && (a = b)}；
 *       {@code a ??= b} → {@code a !== null && a !== undefined ? a : (a = b)}</li>
 * </ul>
 *
 * <p>{@code ??=} 特意展开成显式的 null/undefined 判断，而不是复用 {@code ??}：
 * 当前 Rhino 会算错 {@code ??}（右侧被整个忽略），绕过它才是安全的。</p>
 *
 * <p>判断同样在 {@link ModernJSMask} 的掩码文本上做，所以字符串、注释、正则、
 * 模板串里的这些东西不会被误改。</p>
 *
 * <p>已知局限：逻辑赋值只认「简单标识符或点号成员链」当左值。形如
 * {@code arr[i] ||= v}、{@code o.f().x ||= v} 这类左侧带下标的写法不转换，
 * 会原样留给 Rhino 报语法错误（响亮失败，不会静默算错）；另外成员链左值
 * 在展开后会被求值两次（{@code o.x || (o.x = v)}），若 {@code o.x} 是带副作用的
 * getter 会多跑一次，这种情况很少见，暂时接受。</p>
 */
final class ModernJSSugarConverter {

    /** 可选 catch 补出来的形参名，取个不容易撞车的 */
    private static final String CATCH_PARAM = "__kl_catch";

    private static final int MAX_ROUNDS = 256;

    private ModernJSSugarConverter() {
    }

    /** 依次降级三种语法糖。 */
    static String convert(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = convertNumericSeparators(text);
        result = convertOptionalCatch(result);
        result = convertLogicalAssignments(result);
        return result;
    }

    /* ===================== 数字分隔符 ===================== */

    /** 去掉数字之间的下划线：{@code 1_000_000} → {@code 1000000}。 */
    private static String convertNumericSeparators(String text) {
        if (text.indexOf('_') < 0) {
            return text;
        }
        String masked = ModernJSMask.mask(text);
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean inCode = masked.charAt(i) != ModernJSMask.MASK;
            if (c == '_' && inCode && i > 0 && i + 1 < text.length()
                    && Character.isDigit(masked.charAt(i - 1))
                    && Character.isDigit(masked.charAt(i + 1))) {
                continue; // 数字之间：分隔符，去掉
            }
            out.append(c);
        }
        return out.toString();
    }

    /* ===================== 可选 catch 绑定 ===================== */

    /** {@code catch {} } → {@code catch (__kl_catch) {} }（已带形参的不动）。 */
    private static String convertOptionalCatch(String text) {
        if (!text.contains("catch")) {
            return text;
        }
        String masked = ModernJSMask.mask(text);
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            if (masked.charAt(i) != ModernJSMask.MASK
                    && text.startsWith("catch", i)
                    && !isIdentifierChar(charAt(masked, i - 1))
                    && !isIdentifierChar(charAt(masked, i + 5))) {
                int j = i + 5;
                while (j < text.length() && Character.isWhitespace(masked.charAt(j))) {
                    j++;
                }
                if (j < text.length() && masked.charAt(j) == '{') {
                    // 形参名不会和用户代码冲突，也不是下划线开头以外的新符号
                    out.append("catch (").append(CATCH_PARAM).append(") ");
                    i = j;
                    continue;
                }
            }
            out.append(text.charAt(i));
            i++;
        }
        return out.toString();
    }

    /* ===================== 逻辑赋值 ===================== */

    /**
     * 逻辑赋值降级。
     *
     * <p>每轮只改写<b>最后</b>一处：这样它的右侧不可能再包含别的待改写处
     * （否则那一处才更靠后），先改哪一处都不会影响前面那些的位置。</p>
     */
    private static String convertLogicalAssignments(String text) {
        String result = text;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            String masked = ModernJSMask.mask(result);
            if (masked.indexOf('=') < 0) {
                return result;
            }

            boolean changed = false;
            for (int at = lastOperator(masked); at >= 0; at = lastOperator(masked.substring(0, at))) {
                String op = masked.startsWith("||=", at) ? "||"
                        : (masked.startsWith("&&=", at) ? "&&" : "??");
                // 左值与运算符之间可能有空白，先退掉再扫引用
                int lhsEnd = at;
                while (lhsEnd > 0 && Character.isWhitespace(masked.charAt(lhsEnd - 1))) {
                    lhsEnd--;
                }
                int lhsStart = lhsEnd;
                while (lhsStart > 0 && isRefChar(masked.charAt(lhsStart - 1))) {
                    lhsStart--;
                }
                String lhs = result.substring(lhsStart, lhsEnd).trim();
                if (lhs.isEmpty() || !isSimpleReference(lhs)) {
                    continue; // 左值太复杂：不冒险，原样留给 Rhino 报错
                }

                int rhsStart = at + 3;
                int rhsEnd = findRhsEnd(masked, rhsStart);
                String rhs = result.substring(rhsStart, rhsEnd).trim();
                if (rhs.isEmpty()) {
                    continue;
                }

                String replacement = switch (op) {
                    case "||" -> lhs + " || (" + lhs + " = " + rhs + ")";
                    case "&&" -> lhs + " && (" + lhs + " = " + rhs + ")";
                    default -> lhs + " !== null && " + lhs + " !== undefined ? "
                            + lhs + " : (" + lhs + " = " + rhs + ")";
                };

                result = result.substring(0, lhsStart) + replacement + result.substring(rhsEnd);
                changed = true;
                break;
            }

            if (!changed) {
                return result;
            }
        }
        return result;
    }

    /** 找最后一处 {@code ||= / &&= / ??=} 的下标；没有返回 -1。 */
    private static int lastOperator(String masked) {
        for (int i = masked.length() - 3; i >= 0; i--) {
            if (masked.charAt(i) == ModernJSMask.MASK) {
                continue;
            }
            if (masked.startsWith("||=", i) || masked.startsWith("&&=", i) || masked.startsWith("??=", i)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 找到右值表达式的结束位置。
     *
     * <p>在顶层（括号深度 0）遇到 {@code ; , ) ] }} 或换行就结束。换行要排除
     * 「上一行以运算符结尾」和「下一行以 . 开头」这两种续行写法，否则多行表达式会被切坏。</p>
     */
    private static int findRhsEnd(String masked, int from) {
        int depth = 0;
        for (int i = from; i < masked.length(); i++) {
            char c = masked.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                if (depth == 0) {
                    return i;
                }
                depth--;
            } else if (depth == 0) {
                if (c == ';' || c == ',') {
                    return i;
                }
                if (c == '\n') {
                    int next = i + 1;
                    while (next < masked.length() && Character.isWhitespace(masked.charAt(next))) {
                        next++;
                    }
                    if (next < masked.length() && masked.charAt(next) == '.') {
                        continue; // 续行：下一行以 . 开头
                    }
                    if (continuesLine(masked, i)) {
                        continue; // 续行：本行以运算符结尾
                    }
                    return i;
                }
            }
        }
        return masked.length();
    }

    /** 换行前最后一个有效字符是不是「等待右操作数」的运算符。 */
    private static boolean continuesLine(String masked, int newlineIndex) {
        int i = newlineIndex - 1;
        while (i >= 0 && (masked.charAt(i) == ' ' || masked.charAt(i) == '\t' || masked.charAt(i) == '\r')) {
            i--;
        }
        if (i < 0) {
            return false;
        }
        char c = masked.charAt(i);
        return switch (c) {
            case '+', '-', '*', '/', '%', '=', '<', '>', '&', '|', '^', '!', '?', ':', ',', '(' , '[', '{' -> true;
            default -> false;
        };
    }

    /* ===================== 小工具 ===================== */

    private static char charAt(String s, int index) {
        return index >= 0 && index < s.length() ? s.charAt(index) : '\0';
    }

    private static boolean isRefChar(char c) {
        return isIdentifierChar(c) || c == '.';
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /** 左值是否形如 {@code name} 或 {@code a.b.c}（不带下标、不带调用）。 */
    private static boolean isSimpleReference(String s) {
        int dot = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.') {
                if (i == 0 || i == s.length() - 1 || dot == i - 1) {
                    return false;
                }
                dot = i;
                continue;
            }
            if (!isIdentifierChar(c)) {
                return false;
            }
            if (i == 0 && Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }
}
