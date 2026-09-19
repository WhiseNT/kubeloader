package com.whisent.kubeloader.scripts;

import java.util.ArrayList;
import java.util.List;

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

    /** 依次降级各种语法糖。 */
    static String convert(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = convertNumericSeparators(text);
        result = convertOptionalCatch(result);
        result = convertLogicalAssignments(result);
        result = convertComputedKeys(result);
        return convertPatternDefaults(result);
    }

    /* ===================== 解构的模式侧默认值 ===================== */

    /** 摊平解构模式时用的临时变量前缀 */
    private static final String DESTR_TMP = "__kl_d";

    /**
     * 解构模式里的默认值降级。
     *
     * <p>Rhino 不支持这种写法，而且报错信息很误导：改名与数组形式会老实地报
     * {@code Default values in destructuring declarations are not supported}，
     * 最普通的 {@code {a = 1}} 却报 {@code missing ( before function parameters}
     * ——这实测是 Rhino 自己的说法，跟转换器没关系。
     * 这里把带默认值的模式摊平成普通语句：</p>
     * <pre>
     *   var {a = 1} = x;        →  var a = x["a"] === undefined ? 1 : x["a"];
     *   var {a: b = 2} = x;     →  var b = x["a"] === undefined ? 2 : x["a"];
     *   var [x = 9] = arr;      →  var x = arr[0] === undefined ? 9 : arr[0];
     *   function f({n = 3}) {}  →  function f(__kl_dp0) { var n = __kl_dp0["n"] === undefined ? 3 : __kl_dp0["n"]; }
     * </pre>
     *
     * <p>右侧不是简单标识符时会先存进临时变量，保证只求值一次（{@code f()} 不会被调两次）。</p>
     *
     * <p>只处理「模式紧跟 var/let/const」与「函数形参」两种位置，且每个成员都得是普通标识符。
     * 下面这些形式一概原样留着，让 Rhino 直接报语法错误（响亮失败，不会静默算错值）：
     * 嵌套模式（{@code {a: {b = 1}}}）、模式里的数组 rest（{@code [a, ...r]}）、
     * for-of / for-in（{@code for (var {a = 1} of ...)}）、不带声明的解构赋值
     * （{@code ({a = 1} = o)}）、箭头函数形参。没有默认值的普通解构 Rhino 原生支持，
     * 这里也不会去动（见 {@link #hasLeftoverPatternDefault} 处的说明）。</p>
     */
    private static String convertPatternDefaults(String text) {
        if (text.indexOf('=') < 0) {
            return text;
        }
        String result = text;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            String masked = ModernJSMask.mask(result);
            Rewrite rewrite = findPatternRewrite(masked, result, round);
            if (rewrite == null) {
                return result;
            }
            result = result.substring(0, rewrite.start) + rewrite.replacement + result.substring(rewrite.end);
        }
        return result;
    }

    /**
     * 转换之后源码里是否还留着「带默认值的解构模式」。
     *
     * <p>支持的形式都被摊平了，所以转换结果里还能看到这种模式，就说明遇上了上面列的
     * 那些不支持改写的形式。只用来给报错补一句人话：Rhino 对 {@code {a = 1}} 这类写法
     * 报的是 {@code missing ( before function parameters}，完全看不出跟解构有关。</p>
     *
     * <p>只认「var/let/const 后面」和「{@code (} 后面」（箭头/函数形参、解构赋值）两个位置，
     * 且模式内部任意深度上要有真正的 {@code =}，免得把对象字面量、代码块误判进来。
     * 放宽到「任意深度」是为了能看到嵌套模式里的默认值（{@code {a: {b = 1}}}）。</p>
     */
    static boolean hasLeftoverPatternDefault(String text) {
        if (text == null || text.indexOf('=') < 0) {
            return false;
        }
        String masked = ModernJSMask.mask(text);
        for (int i = 0; i < masked.length(); i++) {
            char c = masked.charAt(i);
            if (c != '{' && c != '[') {
                continue;
            }
            int j = i;
            while (j > 0 && Character.isWhitespace(masked.charAt(j - 1))) {
                j--;
            }
            int wordEnd = j;
            while (j > 0 && isIdentifierChar(masked.charAt(j - 1))) {
                j--;
            }
            String word = masked.substring(j, wordEnd);
            boolean declSite = "var".equals(word) || "let".equals(word) || "const".equals(word)
                    || (word.isEmpty() && j > 0 && masked.charAt(j - 1) == '(');
            if (!declSite) {
                continue;
            }
            int close = ModernJSMask.matchClose(masked, i);
            if (close < 0) {
                continue;
            }
            if (hasAssignAnywhere(masked, i + 1, close)) {
                return true;
            }
        }
        return false;
    }

    /** 从右往左找一处可改写的解构默认值：改最靠后的一处，不影响左边内容的下标。 */
    private static Rewrite findPatternRewrite(String masked, String original, int round) {
        for (int i = masked.length() - 1; i >= 0; i--) {
            char c = masked.charAt(i);
            if (c == '{' || c == '[') {
                Rewrite rewrite = tryDeclarationPattern(masked, original, i, round);
                if (rewrite != null) {
                    return rewrite;
                }
                continue;
            }
            if (c == 'f' && masked.startsWith("function", i)
                    && !isIdentifierChar(charAt(masked, i - 1))
                    && !isIdentifierChar(charAt(masked, i + 8))) {
                Rewrite rewrite = tryFunctionParams(masked, original, i);
                if (rewrite != null) {
                    return rewrite;
                }
            }
        }
        return null;
    }

    /** var/let/const {…} = rhs 形式。 */
    private static Rewrite tryDeclarationPattern(String masked, String original, int open, int round) {
        int i = open;
        while (i > 0 && Character.isWhitespace(masked.charAt(i - 1))) {
            i--;
        }
        int wordEnd = i;
        while (i > 0 && isIdentifierChar(masked.charAt(i - 1))) {
            i--;
        }
        String keyword = masked.substring(i, wordEnd);
        if (!"var".equals(keyword) && !"let".equals(keyword) && !"const".equals(keyword)) {
            return null; // 不是声明语句里的模式
        }
        int close = ModernJSMask.matchClose(masked, open);
        if (close < 0) {
            return null;
        }
        // 模式里一个默认值都没有的话，Rhino 原生就支持这种解构（{a} / [x, y] / {a: b}），
        // 不用动它：无谓改写只会平白引入行为差异。
        if (topLevelAssign(masked, open + 1, close) < 0) {
            return null;
        }
        int eq = close + 1;
        while (eq < masked.length() && Character.isWhitespace(masked.charAt(eq))) {
            eq++;
        }
        if (eq >= masked.length() || masked.charAt(eq) != '=' || masked.startsWith("==", eq)) {
            return null; // 解构赋值（没有声明）或 for-of 之类，不在这次范围内
        }
        int rhsStart = eq + 1;
        int rhsEnd = findRhsEnd(masked, rhsStart);
        String rhs = original.substring(rhsStart, rhsEnd).trim();
        if (rhs.isEmpty()) {
            return null;
        }

        String prefix;
        String source;
        if (isIdentifier(rhs) || "this".equals(rhs)) {
            prefix = keyword + " ";
            source = rhs;
        } else {
            String tmp = DESTR_TMP + round;
            prefix = keyword + " " + tmp + " = " + rhs + ", ";
            source = tmp;
        }
        String bindings = buildPatternBindings(masked, original, open, close, source);
        if (bindings == null) {
            return null;
        }
        return new Rewrite(i, rhsEnd, prefix + bindings);
    }

    /** 函数形参里的解构模式（带默认值的那些换成临时变量，并在函数体开头摊平）。 */
    private static Rewrite tryFunctionParams(String masked, String original, int funcStart) {
        int i = funcStart + 8;
        while (i < masked.length() && Character.isWhitespace(masked.charAt(i))) {
            i++;
        }
        while (i < masked.length() && isIdentifierChar(masked.charAt(i))) {
            i++; // 可选函数名
        }
        while (i < masked.length() && Character.isWhitespace(masked.charAt(i))) {
            i++;
        }
        if (i >= masked.length() || masked.charAt(i) != '(') {
            return null;
        }
        int open = i;
        int close = ModernJSMask.matchClose(masked, open);
        if (close < 0) {
            return null;
        }
        int brace = close + 1;
        while (brace < masked.length() && Character.isWhitespace(masked.charAt(brace))) {
            brace++;
        }
        if (brace >= masked.length() || masked.charAt(brace) != '{') {
            return null; // 没有函数体（箭头函数之类）：不冒险
        }

        List<String> params = new ArrayList<>();
        StringBuilder stmts = new StringBuilder();
        boolean changed = false;
        int index = 0;

        for (int[] p : ModernJSMask.splitTopLevel(masked, open + 1, close)) {
            String param = original.substring(p[0], p[1]).trim();
            if (param.isEmpty()) {
                continue;
            }
            char first = param.charAt(0);
            if (first != '{' && first != '[') {
                params.add(param); // 普通参数：Rhino 自己能处理，不动
                continue;
            }
            int patOpen = p[0];
            while (patOpen < p[1] && Character.isWhitespace(masked.charAt(patOpen))) {
                patOpen++;
            }
            if (masked.charAt(patOpen) != '{' && masked.charAt(patOpen) != '[') {
                params.add(param);
                continue;
            }
            int patClose = ModernJSMask.matchClose(masked, patOpen);
            if (patClose < 0 || patClose > p[1]) {
                return null;
            }
            int after = patClose + 1;
            while (after < p[1] && Character.isWhitespace(masked.charAt(after))) {
                after++;
            }
            if (after < p[1]) {
                return null; // 形如 {a = 1} = {}（整个模式再带默认值）：不在这次范围内
            }
            // 关键：要看模式「内部」有没有默认值。把模式的 {} / [] 一起算进跨度的话，
            // 里面的 '=' 会落在深度 1 上，被当成「没有默认值」直接跳过。
            if (topLevelAssign(masked, patOpen + 1, patClose) < 0) {
                params.add(param); // 没有默认值的模式：Rhino 自己能处理，不动
                continue;
            }
            String tmp = DESTR_TMP + "p" + index;
            index++;
            String bindings = buildPatternBindings(masked, original, patOpen, patClose, tmp);
            if (bindings == null) {
                return null;
            }
            params.add(tmp);
            stmts.append("var ").append(bindings).append(";\n");
            changed = true;
        }

        if (!changed) {
            return null;
        }
        return new Rewrite(open, brace + 1, "(" + String.join(", ", params) + ") {\n" + stmts);
    }

    /**
     * 把一个模式摊平成「绑定赋值」列表（逗号分隔）；有搞不定的成员返回 null。
     *
     * @param source 取值的来源表达式：可能是原样标识符，也可能是临时变量名
     */
    private static String buildPatternBindings(String masked, String original, int open, int close, String source) {
        char opener = masked.charAt(open);
        List<String> bindings = new ArrayList<>();
        int index = 0;

        for (int[] p : ModernJSMask.splitTopLevel(masked, open + 1, close)) {
            String member = original.substring(p[0], p[1]).trim();
            if (member.isEmpty()) {
                index++; // 数组模式里的空位（[, a]）也要占一个下标
                continue;
            }
            if (member.startsWith("...")) {
                return null; // 数组 rest：不在这次范围内
            }

            int assign = topLevelAssign(masked, p[0], p[1]);
            String accessExpr;
            int targetFrom;

            if (opener == '{') {
                if (member.startsWith("{") || member.startsWith("[")) {
                    return null; // 嵌套模式：不在这次范围内
                }
                // 顺序很重要：先找默认值分隔符（顶层 '='），再在它之前找 ':'。
                // 否则 {a = 1} 这种「简写 + 默认值」会把 "a = 1" 整个当成键名。
                int valueEnd = assign >= 0 ? assign : p[1];
                int colon = topLevelColon(masked, p[0], valueEnd);
                String key = original.substring(p[0], colon >= 0 ? colon : valueEnd).trim();
                if (key.isEmpty()) {
                    return null;
                }
                boolean quoted = key.charAt(0) == '"' || key.charAt(0) == '\'';
                if (quoted) {
                    accessExpr = source + "[" + key + "]";
                } else if (isIdentifierOrNumber(key)) {
                    // 一律加引号：写成 source[a] 会被当成变量，也顺便避开保留字做键名
                    accessExpr = source + "[\"" + key + "\"]";
                } else {
                    return null; // 计算属性名之类的模式，不处理
                }
                targetFrom = colon >= 0 ? colon + 1 : p[0];
            } else {
                if (member.startsWith("{") || member.startsWith("[")) {
                    return null; // 嵌套模式：不在这次范围内
                }
                accessExpr = source + "[" + index + "]";
                targetFrom = p[0];
            }

            String target = original.substring(targetFrom, assign >= 0 ? assign : p[1]).trim();
            if (!isIdentifier(target)) {
                return null; // 目标不是普通标识符（嵌套 / 成员访问等）
            }
            index++;

            if (assign < 0) {
                bindings.add(target + " = " + accessExpr);
            } else {
                String fallback = original.substring(assign + 1, p[1]).trim();
                if (fallback.isEmpty()) {
                    return null;
                }
                bindings.add(target + " = " + accessExpr + " === undefined ? " + fallback + " : " + accessExpr);
            }
        }

        return bindings.isEmpty() ? null : String.join(", ", bindings);
    }

    /** 位置 {@code i} 上的 {@code =} 是不是「真正的赋值等号」（不是 == / === / => / 复合赋值的一部分）。 */
    private static boolean isAssignEquals(String masked, int i) {
        char prev = charAt(masked, i - 1);
        char next = charAt(masked, i + 1);
        if (next == '=' || next == '>') {
            return false;
        }
        return prev != '=' && prev != '!' && prev != '<' && prev != '>' && prev != '+'
                && prev != '-' && prev != '*' && prev != '/' && prev != '%' && prev != '&'
                && prev != '|' && prev != '^';
    }

    /** [from, to) 里有没有真正的赋值等号（忽略括号深度）；用于「模式里到底有没有默认值」。 */
    private static boolean hasAssignAnywhere(String masked, int from, int to) {
        for (int i = from; i < to; i++) {
            if (masked.charAt(i) == '=' && isAssignEquals(masked, i)) {
                return true;
            }
        }
        return false;
    }

    /** 找到 [from, to) 里第一个「真正的赋值等号」（排除 == / === / => / <= / >= / != 等运算符的一部分）；没有返回 -1。 */
    private static int topLevelAssign(String masked, int from, int to) {
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

    /* ===================== 计算属性名 ===================== */

    /** 建临时对象用的变量名，取个不容易撞车的 */
    private static final String OBJ_TMP = "__kl_o";

    /** 一次改写：[start, end) 换成 replacement。 */
    private static final class Rewrite {
        final int start;
        final int end;
        final String replacement;

        Rewrite(int start, int end, String replacement) {
            this.start = start;
            this.end = end;
            this.replacement = replacement;
        }
    }

    /**
     * 对象字面量里的计算属性名 {@code {[expr]: v}} 降级。
     *
     * <p>Rhino 对这种写法直接报 {@code invalid property id}。这里把整个对象改写成
     * 「先建空对象、再按书写顺序逐个赋值」的 IIFE：</p>
     * <pre>
     *   {a: 1, [k]: 2}  →  (function () { var __kl_o = {}; __kl_o["a"] = 1; __kl_o[k] = 2; return __kl_o; })()
     * </pre>
     *
     * <p>展开成员 {@code ...x} 交给 {@code Object.assign}（与 `...` 的降级保持一致），
     * 顺序也照写：后面的覆盖前面的。</p>
     *
     * <p>遇到存取器（get/set）或方法简写就<b>不动</b>这个对象——那两种成员没法简单地
     * 搬进 IIFE，原样留着让 Rhino 报语法错误，比改出错误结果安全。</p>
     */
    private static String convertComputedKeys(String text) {
        if (text.indexOf('[') < 0) {
            return text;
        }
        String result = text;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            String masked = ModernJSMask.mask(result);
            Rewrite rewrite = findComputedKeyRewrite(masked, result);
            if (rewrite == null) {
                return result;
            }
            result = result.substring(0, rewrite.start) + rewrite.replacement + result.substring(rewrite.end);
        }
        return result;
    }

    /**
     * 找含计算属性名成员的对象字面量。
     *
     * <p>从右往左找最内层那个：改它不会影响它左边任何内容的下标。</p>
     */
    private static Rewrite findComputedKeyRewrite(String masked, String original) {
        for (int i = masked.length() - 1; i >= 0; i--) {
            if (masked.charAt(i) != '{') {
                continue;
            }
            int close = ModernJSMask.matchClose(masked, i);
            if (close <= i || followedByAssignment(masked, close)) {
                continue; // 不配对，或是解构模式（`var {[k]: x} = o` 这种不能按字面量改）
            }
            List<int[]> parts = ModernJSMask.splitTopLevel(masked, i + 1, close);
            if (!hasComputedMember(masked, parts)) {
                continue;
            }
            String replacement = buildComputedObject(masked, original, parts);
            if (replacement != null) {
                return new Rewrite(i, close + 1, replacement);
            }
        }
        return null;
    }

    private static boolean hasComputedMember(String masked, List<int[]> parts) {
        for (int[] p : parts) {
            int i = p[0];
            while (i < p[1] && Character.isWhitespace(masked.charAt(i))) {
                i++;
            }
            if (i < p[1] && masked.charAt(i) == '[' && isComputedKey(masked, i, p[1])) {
                return true;
            }
        }
        return false;
    }

    /** '[' 起头、且配对的 ']' 后面（跳过空白）是 ':' —— 这就是计算属性名。 */
    private static boolean isComputedKey(String masked, int bracket, int memberEnd) {
        int close = ModernJSMask.matchClose(masked, bracket);
        if (close < 0 || close >= memberEnd) {
            return false;
        }
        int j = close + 1;
        while (j < memberEnd && Character.isWhitespace(masked.charAt(j))) {
            j++;
        }
        return j < memberEnd && masked.charAt(j) == ':';
    }

    /**
     * 把对象字面量的成员拼成 IIFE 里的赋值序列；碰到搞不定的成员返回 null（整个对象不动）。
     */
    private static String buildComputedObject(String masked, String original, List<int[]> parts) {
        StringBuilder assigns = new StringBuilder();
        for (int[] p : parts) {
            int from = p[0];
            String member = original.substring(from, p[1]).trim();
            if (member.isEmpty()) {
                continue; // 尾逗号留下的空段
            }
            if (member.startsWith("...")) {
                assigns.append("Object.assign(").append(OBJ_TMP).append(", ")
                        .append(member.substring(3).trim()).append(");\n");
                continue;
            }
            if (member.startsWith("get ") || member.startsWith("set ")) {
                return null; // 存取器搬不进这种形式
            }

            // 计算属性名
            int i = from;
            while (i < p[1] && Character.isWhitespace(masked.charAt(i))) {
                i++;
            }
            if (i < p[1] && masked.charAt(i) == '[') {
                int bracketClose = ModernJSMask.matchClose(masked, i);
                if (bracketClose < 0 || bracketClose >= p[1]) {
                    return null;
                }
                String keyExpr = original.substring(i + 1, bracketClose).trim();
                int j = bracketClose + 1;
                while (j < p[1] && Character.isWhitespace(masked.charAt(j))) {
                    j++;
                }
                if (j >= p[1] || masked.charAt(j) != ':' || keyExpr.isEmpty()) {
                    return null;
                }
                String value = original.substring(j + 1, p[1]).trim();
                if (value.isEmpty()) {
                    return null;
                }
                assigns.append(OBJ_TMP).append('[').append(keyExpr).append("] = ").append(value).append(";\n");
                continue;
            }

            // 普通 key: value
            int colon = topLevelColon(masked, from, p[1]);
            if (colon < 0) {
                // 没有冒号：只认「简写属性」（{a}），方法简写之类一概不动
                if (!isIdentifier(member)) {
                    return null;
                }
                assigns.append(OBJ_TMP).append("[\"").append(member).append("\"] = ").append(member).append(";\n");
                continue;
            }
            String key = original.substring(from, colon).trim();
            String value = original.substring(colon + 1, p[1]).trim();
            if (key.isEmpty() || value.isEmpty()) {
                return null;
            }
            boolean quoted = key.charAt(0) == '"' || key.charAt(0) == '\'';
            if (!quoted && !isIdentifierOrNumber(key)) {
                return null;
            }
            // 标识符/数字键要加引号：写成 __kl_o[a] 会被当成变量
            assigns.append(OBJ_TMP).append('[').append(quoted ? key : "\"" + key + "\"").append("] = ")
                    .append(value).append(";\n");
        }
        return "(function () {\nvar " + OBJ_TMP + " = {};\n" + assigns + "return " + OBJ_TMP + ";\n})()";
    }

    /** 成员里第一个顶层冒号的位置；没有返回 -1。 */
    private static int topLevelColon(String masked, int from, int to) {
        int depth = 0;
        for (int i = from; i < to; i++) {
            char c = masked.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ':' && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** 闭合括号后面（跳过空白）是不是 = / of / in —— 那是解构模式。 */
    private static boolean followedByAssignment(String masked, int close) {
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
            return "of".equals(word) || "in".equals(word);
        }
        return false;
    }

    private static boolean isIdentifier(String s) {
        if (s.isEmpty() || !isIdentifierStart(s.charAt(0))) {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            if (!isIdentifierChar(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIdentifierOrNumber(String s) {
        if (isIdentifier(s)) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return !s.isEmpty();
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
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
            if (c == '_' && inCode && isNumericSeparator(masked, i)) {
                continue; // 数字之间：分隔符，去掉
            }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * 位置 {@code i} 上的 {@code _} 是不是数字分隔符。
     *
     * <p>要同时满足「在数字字面量里」和「两侧都是该字面量的合法数字字符」。
     * 只看两侧是不是十进制数字会把 {@code 0xFF_FF} 漏掉（{@code F} 不是十进制数字）；
     * 放宽成「十六进制字母也算」又会误伤 {@code cafe_bar} 这种标识符。
     * 所以先往回找到 token 开头，确认它是个数字字面量，再按进制判断两侧。</p>
     */
    private static boolean isNumericSeparator(String masked, int i) {
        if (i <= 0 || i + 1 >= masked.length()) {
            return false;
        }
        int start = i - 1;
        while (start > 0 && isIdentifierChar(masked.charAt(start - 1))) {
            start--;
        }
        // token 必须以数字开头才算数字字面量（cafe_bar 是 'c' 开头，直接否掉）
        if (!Character.isDigit(masked.charAt(start))) {
            return false;
        }
        boolean hex = masked.startsWith("0x", start) || masked.startsWith("0X", start);
        return isDigitOfLiteral(masked.charAt(i - 1), hex)
                && isDigitOfLiteral(masked.charAt(i + 1), hex);
    }

    private static boolean isDigitOfLiteral(char c, boolean hex) {
        if (Character.isDigit(c)) {
            return true;
        }
        return hex && ((c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'));
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
