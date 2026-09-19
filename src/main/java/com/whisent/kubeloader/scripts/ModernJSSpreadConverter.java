package com.whisent.kubeloader.scripts;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 {@code ...} 相关的 ES6 写法降到当前 Rhino 能跑的写法。
 *
 * <p>Rhino 完全不认 {@code ...}（连解析都过不了），所以这些写法必须在这里处理掉：</p>
 * <ul>
 *   <li>调用展开：{@code f(a, ...b)} → {@code f.apply(null, [a].concat(__kl_spread(b)))}</li>
 *   <li>成员调用展开：{@code o.m(...b)} → {@code o.m.apply(o, __kl_spread(b))}，保留 this</li>
 *   <li>构造调用展开：{@code new F(...b)} → {@code __kl_new(F, __kl_spread(b))}</li>
 *   <li>数组字面量展开：{@code [a, ...b]} → {@code [a].concat(__kl_spread(b))}</li>
 *   <li>对象字面量展开：{@code {...a, y: 2}} → {@code Object.assign({}, a, {y: 2})}</li>
 *   <li>剩余参数：{@code function f(a, ...rest) {}} → {@code function f(a) { var rest = ...; }}</li>
 * </ul>
 *
 * <p>所有结构判断都在<b>掩码文本</b>上做：字符串、注释、正则、模板串会被替换成同长度的
 * 占位字符，于是它们里面的 {@code ...} 和括号都不会被误当成代码。取内容时回到原文，
 * 因为长度不变，下标始终对得上。</p>
 *
 * <p>辅助函数以<b>函数声明</b>的形式放在脚本最前面：声明会被提升，所以后面的调用能用；
 * 放在最前面而不是末尾，是为了不改变脚本的最后一条语句（也就是它的「完成值」）。</p>
 *
 * <p>已知局限：模板串 {@code ${}} 内部不参与检查（整个模板被掩码），那里的 {@code ...}
 * 会原样留下，由 Rhino 报语法错误（响亮失败，不会静默算错）；对象展开走 Object.assign，
 * 因此源对象上的 getter 会被求值成值（与原生略有差异）。</p>
 */
final class ModernJSSpreadConverter {

    /** 一轮只改写一处，嵌套写法靠多轮收敛；给个上限兜底 */
    private static final int MAX_ROUNDS = 64;

    private ModernJSSpreadConverter() {
    }

    /** 转换文本里的所有 {@code ...}；没用到就原样返回。 */
    static String convert(String text) {
        if (text == null || !text.contains("...")) {
            return text;
        }

        String result = text;
        boolean needSpread = false;
        boolean needNew = false;

        for (int round = 0; round < MAX_ROUNDS; round++) {
            String masked = ModernJSMask.mask(result);
            Rewrite rewrite = findRewrite(masked, result);
            if (rewrite == null) {
                break;
            }
            result = result.substring(0, rewrite.start) + rewrite.replacement + result.substring(rewrite.end);
            needSpread |= rewrite.needSpread;
            needNew |= rewrite.needNew;
        }

        if (!needSpread && !needNew) {
            return result;
        }
        StringBuilder helper = new StringBuilder();
        if (needSpread) {
            helper.append(SPREAD_HELPER);
        }
        if (needNew) {
            helper.append(NEW_HELPER);
        }
        // 放在最前面：函数声明会被提升，所以后面的调用照样能用；而脚本的最后一条语句
        // 仍然是用户写的那条，脚本的「完成值」不会被这几行辅助代码改写
        return helper + "\n" + result;
    }

    /* ===================== 一次改写 ===================== */

    private static final class Rewrite {
        final int start;
        final int end;
        final String replacement;
        final boolean needSpread;
        final boolean needNew;

        Rewrite(int start, int end, String replacement, boolean needSpread, boolean needNew) {
            this.start = start;
            this.end = end;
            this.replacement = replacement;
            this.needSpread = needSpread;
            this.needNew = needNew;
        }
    }

    /**
     * 找出「最内层」那处含 {@code ...} 的构造并给出改写方案；没有再返回 null。
     *
     * <p>一次只改一处，改完重新扫描，嵌套的写法会在后续轮次里自然收敛。</p>
     */
    private static Rewrite findRewrite(String masked, String original) {
        int n = masked.length();
        List<int[]> stack = new ArrayList<>(); // {openIndex, openChar}

        for (int i = 0; i < n; i++) {
            char c = masked.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                stack.add(new int[]{i, c});
                continue;
            }
            if (c == ')' || c == ']' || c == '}') {
                if (!stack.isEmpty()) {
                    stack.remove(stack.size() - 1);
                }
                continue;
            }
            if (c != '.' || i + 2 >= n || masked.charAt(i + 1) != '.' || masked.charAt(i + 2) != '.') {
                continue;
            }
            if (stack.isEmpty()) {
                i += 2;
                continue; // 没有外层构造（例如正则残留），不是这几种写法
            }
            int open = stack.get(stack.size() - 1)[0];
            int close = ModernJSMask.matchClose(masked, open);
            if (close > open) {
                Rewrite rewrite = buildRewrite(masked, original, open, close);
                if (rewrite != null) {
                    return rewrite;
                }
            }
            i += 2;
        }
        return null;
    }

    private static Rewrite buildRewrite(String masked, String original, int open, int close) {
        char opener = masked.charAt(open);

        if (opener == '(') {
            if (isFunctionParamList(masked, open)) {
                return buildRestParams(masked, original, open, close);
            }
            if (isControlParen(masked, open) || isArrowParamList(masked, close)) {
                return null; // 控制语句的括号 / 箭头函数参数：不是能安全改写的位置
            }
            return buildCallSpread(masked, original, open, close);
        }

        if (opener == '[') {
            if (!isArrayLiteral(masked, open) || isDestructuringPattern(masked, close)) {
                return null;
            }
            return buildArraySpread(masked, original, open, close);
        }

        return buildObjectSpread(masked, original, open, close);
    }

    /* ===================== 各构造的改写 ===================== */

    /** 调用展开：f(a, ...b) / o.m(...b) / new F(...b) */
    private static Rewrite buildCallSpread(String masked, String original, int open, int close) {
        List<int[]> parts = ModernJSMask.splitTopLevel(masked, open + 1, close);
        if (!hasSpreadPart(masked, parts)) {
            return null;
        }

        int calleeStart = open;
        // 调用括号前可能有空格（`f (...args)` / `o.m (...args)`），先跳过再取被调用者
        while (calleeStart > 0 && Character.isWhitespace(masked.charAt(calleeStart - 1))) {
            calleeStart--;
        }
        int afterWhitespace = calleeStart;
        while (calleeStart > 0 && isCalleeChar(masked.charAt(calleeStart - 1))) {
            calleeStart--;
        }
        if (calleeStart == afterWhitespace) {
            return null; // 形如 (...) 的分组括号，没有被调用的东西
        }
        // 成员链被空白断开（`o . m(...)` / `o . m (...)`）时，回扫只拿到方法名，
        // 前面还有个 '.' —— 这时 callee 是残缺的。与其退化成 apply(null, ...) 把 this
        // 弄丢（非严格模式下 this 是全局对象，方法里读 this.x 静默得到 undefined），
        // 不如不改写，留着 `...` 让 Rhino 报语法错误（响亮失败）。
        int probe = calleeStart;
        while (probe > 0 && Character.isWhitespace(masked.charAt(probe - 1))) {
            probe--;
        }
        if (probe > 0 && masked.charAt(probe - 1) == '.') {
            return null;
        }
        String callee = original.substring(calleeStart, open).trim();
        if (callee.isEmpty()) {
            return null;
        }

        String args = buildArgsArray(masked, original, parts);

        int wordStart = calleeStart;
        while (wordStart > 0 && Character.isWhitespace(masked.charAt(wordStart - 1))) {
            wordStart--;
        }
        if (endsWithNewKeyword(masked, wordStart)) {
            int newStart = wordStart - 3;
            return new Rewrite(newStart, close + 1, "__kl_new(" + callee + ", " + args + ")", true, true);
        }

        int dot = callee.lastIndexOf('.');
        if (callee.startsWith(".")) {
            // callee 只剩 ".方法名"，说明接收者被回扫切掉了：接收者是个括号组或字面量
            // （`new X().m(...)`、`f().m(...)`、`'x'.m(...)`）。这种情况原先会落到
            // apply(null, ...)，而非严格模式下 this 会变成全局对象 —— 方法体里读 this.x
            // 静默拿到 undefined（实测直接算出 NaN），正是最危险的静默算错。
            //
            // 要做对必须保证接收者只求值一次（否则 new X() 会被构造两次），得包一层 IIFE；
            // 这里先按「宁可响亮失败，不可静默算错」处理：不改写，留着 `...` 让 Rhino 报语法错误。
            return null;
        }
        if (dot > 0) {
            String receiver = callee.substring(0, dot);
            return new Rewrite(calleeStart, close + 1,
                    callee + ".apply(" + receiver + ", " + args + ")", true, false);
        }
        return new Rewrite(calleeStart, close + 1,
                callee + ".apply(null, " + args + ")", true, false);
    }

    /** 数组字面量展开：[a, ...b] */
    private static Rewrite buildArraySpread(String masked, String original, int open, int close) {
        List<int[]> parts = ModernJSMask.splitTopLevel(masked, open + 1, close);
        if (!hasSpreadPart(masked, parts)) {
            return null;
        }
        return new Rewrite(open, close + 1, buildArgsArray(masked, original, parts), true, false);
    }

    /** 对象字面量展开：{...a, y: 2} */
    private static Rewrite buildObjectSpread(String masked, String original, int open, int close) {
        List<int[]> parts = ModernJSMask.splitTopLevel(masked, open + 1, close);
        if (!hasSpreadPart(masked, parts)) {
            return null;
        }

        StringBuilder out = new StringBuilder("Object.assign({}");
        int i = 0;
        while (i < parts.size()) {
            String part = original.substring(parts.get(i)[0], parts.get(i)[1]).trim();
            if (part.startsWith("...")) {
                out.append(", ").append(part.substring(3).trim());
                i++;
                continue;
            }
            // 连续的普通成员合成一个对象字面量，保持书写顺序（后者覆盖前者）
            int j = i;
            StringBuilder literal = new StringBuilder("{");
            boolean first = true;
            while (j < parts.size()) {
                String p = original.substring(parts.get(j)[0], parts.get(j)[1]).trim();
                if (p.startsWith("...")) {
                    break;
                }
                if (!first) {
                    literal.append(", ");
                }
                literal.append(p);
                first = false;
                j++;
            }
            literal.append("}");
            out.append(", ").append(literal);
            i = j;
        }
        return new Rewrite(open, close + 1, out.append(")").toString(), false, false);
    }

    /** 剩余参数：function f(a, ...rest) { → function f(a) { var rest = ...; */
    private static Rewrite buildRestParams(String masked, String original, int open, int close) {
        List<int[]> parts = ModernJSMask.splitTopLevel(masked, open + 1, close);
        if (parts.isEmpty()) {
            return null;
        }
        int last = parts.size() - 1;
        String lastPart = original.substring(parts.get(last)[0], parts.get(last)[1]).trim();
        if (!lastPart.startsWith("...")) {
            return null; // 剩余参数必须是最后一个，否则是别的写法
        }
        String restName = lastPart.substring(3).trim();
        if (restName.isEmpty() || !isIdentifier(restName)) {
            return null;
        }

        int brace = close + 1;
        while (brace < masked.length() && Character.isWhitespace(masked.charAt(brace))) {
            brace++;
        }
        if (brace >= masked.length() || masked.charAt(brace) != '{') {
            return null; // 函数体不在预期的位置，不冒险
        }

        StringBuilder params = new StringBuilder();
        for (int i = 0; i < last; i++) {
            if (i > 0) {
                params.append(", ");
            }
            params.append(original, parts.get(i)[0], parts.get(i)[1]);
        }

        String replacement = "(" + params + ") {\n"
                + "var " + restName + " = Array.prototype.slice.call(arguments, " + last + ");\n";
        return new Rewrite(open, brace + 1, replacement, false, false);
    }

    /* ===================== 组装参数数组 ===================== */

    /**
     * 把参数表拼成「数组表达式」：展开项交给 __kl_spread，普通项各自包一层数组。
     *
     * <p>{@code [a].concat} 会把数组参数摊平，正好等价于展开。</p>
     */
    private static String buildArgsArray(String masked, String original, List<int[]> parts) {
        StringBuilder out = new StringBuilder("[].concat(");
        for (int i = 0; i < parts.size(); i++) {
            String part = original.substring(parts.get(i)[0], parts.get(i)[1]).trim();
            if (i > 0) {
                out.append(", ");
            }
            if (part.startsWith("...")) {
                out.append("__kl_spread(").append(part.substring(3).trim()).append(")");
            } else if (part.isEmpty()) {
                out.append("[]");
            } else {
                out.append("[").append(part).append("]");
            }
        }
        return out.append(")").toString();
    }

    private static boolean hasSpreadPart(String masked, List<int[]> parts) {
        for (int[] p : parts) {
            int i = p[0];
            while (i < p[1] && Character.isWhitespace(masked.charAt(i))) {
                i++;
            }
            if (i + 2 < p[1] + 1 && i + 2 <= p[1] && masked.startsWith("...", i)) {
                return true;
            }
        }
        return false;
    }

    /* ===================== 位置判断 ===================== */

    /** 打开的 '(' 是不是函数形参表（function f( / function( / ... = function( ）。 */
    private static boolean isFunctionParamList(String masked, int open) {
        int i = open;
        while (i > 0 && Character.isWhitespace(masked.charAt(i - 1))) {
            i--;
        }
        // 形如 "function" 或 "function name"
        int nameEnd = i;
        while (i > 0 && isIdentifierChar(masked.charAt(i - 1))) {
            i--;
        }
        String word = masked.substring(i, nameEnd);
        if ("function".equals(word)) {
            return true;
        }
        if (word.isEmpty()) {
            return false;
        }
        int j = i;
        while (j > 0 && Character.isWhitespace(masked.charAt(j - 1))) {
            j--;
        }
        int kwEnd = j;
        while (j > 0 && isIdentifierChar(masked.charAt(j - 1))) {
            j--;
        }
        return "function".equals(masked.substring(j, kwEnd));
    }

    /** 括号前面是 if/for/while 之类的关键字：这种括号里不会有展开。 */
    private static boolean isControlParen(String masked, int open) {
        int i = open;
        while (i > 0 && Character.isWhitespace(masked.charAt(i - 1))) {
            i--;
        }
        int end = i;
        while (i > 0 && isIdentifierChar(masked.charAt(i - 1))) {
            i--;
        }
        String word = masked.substring(i, end);
        return switch (word) {
            case "if", "for", "while", "switch", "catch", "with" -> true;
            default -> false;
        };
    }

    /** 闭合括号后面跟 => ：这是箭头函数的参数表，语义不同，不在这儿改。 */
    private static boolean isArrowParamList(String masked, int close) {
        int i = close + 1;
        while (i < masked.length() && Character.isWhitespace(masked.charAt(i))) {
            i++;
        }
        return masked.startsWith("=>", i);
    }

    /** '[' 前面不是「表达式延续」时才是数组字面量（否则是下标访问）。 */
    private static boolean isArrayLiteral(String masked, int open) {
        int i = open;
        while (i > 0 && Character.isWhitespace(masked.charAt(i - 1))) {
            i--;
        }
        if (i == 0) {
            return true;
        }
        char before = masked.charAt(i - 1);
        return !(isIdentifierChar(before) || before == ')' || before == ']' || before == '}'
                || before == ModernJSMask.MASK);
    }

    /** 数组字面量后面紧跟 = / of / in：那其实是解构模式，不能按表达式改写。 */
    private static boolean isDestructuringPattern(String masked, int close) {
        int i = close + 1;
        while (i < masked.length() && Character.isWhitespace(masked.charAt(i))) {
            i++;
        }
        if (i >= masked.length()) {
            return false;
        }
        char c = masked.charAt(i);
        if (c == '=' && !masked.startsWith("==", i)) {
            return true;
        }
        if (isIdentifierChar(c)) {
            int end = i;
            while (end < masked.length() && isIdentifierChar(masked.charAt(end))) {
                end++;
            }
            String word = masked.substring(i, end);
            return "of".equals(word) || "in".equals(word) || "instanceof".equals(word);
        }
        return false;
    }

    private static boolean endsWithNewKeyword(String masked, int end) {
        int i = end;
        while (i > 0 && Character.isWhitespace(masked.charAt(i - 1))) {
            i--;
        }
        if (i < 3) {
            return false;
        }
        if (!masked.startsWith("new", i - 3)) {
            return false;
        }
        return i - 3 == 0 || !isIdentifierChar(masked.charAt(i - 4));
    }

    private static boolean isCalleeChar(char c) {
        return isIdentifierChar(c) || c == '.';
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static boolean isIdentifier(String s) {
        if (s.isEmpty()) {
            return false;
        }
        char first = s.charAt(0);
        if (!Character.isLetter(first) && first != '_' && first != '$') {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            if (!isIdentifierChar(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /* ===================== 运行时辅助 ===================== */

    /** 把「可展开的东西」变成数组：数组本身、字符串、可迭代对象、arguments 这类类数组。 */
    private static final String SPREAD_HELPER = """
            function __kl_spread(v) {
                if (v === null || v === undefined) {
                    throw new TypeError('Cannot spread null or undefined');
                }
                if (Array.isArray(v)) {
                    return v;
                }
                if (typeof v === 'string') {
                    return v.split('');
                }
                if (typeof Symbol !== 'undefined' && Symbol.iterator && v[Symbol.iterator]) {
                    var out = [];
                    for (var x of v) {
                        out.push(x);
                    }
                    return out;
                }
                return Array.prototype.slice.call(v);
            }
            """;

    /** new F(...args)：用 apply 构造，并照顾构造函数返回对象的情况。 */
    private static final String NEW_HELPER = """
            function __kl_new(Ctor, args) {
                var obj = Object.create(Ctor.prototype);
                var r = Ctor.apply(obj, args);
                return (r !== null && typeof r === 'object') ? r : obj;
            }
            """;
}
