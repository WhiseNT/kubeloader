package com.whisent.kubeloader.scripts;

/**
 * 补上 Rhino 分支缺失的 {@code let} 「每轮一个新绑定」语义。
 *
 * <p><b>这不是转换器漏转，而是在补引擎的 bug。</b>{@code for (let i = 0; ...)} 里的
 * {@code i} 按标准应当每轮一个新绑定，所以闭包各自记住当轮的值；这个 Rhino 分支只建
 * 一个绑定，于是所有闭包都读到最后一次的值：</p>
 *
 * <pre>
 *   var fs = [];
 *   for (let i = 0; i < 3; i++) { fs.push(function () { return i }) }
 *   // 标准：0,1,2    这个引擎：3,3,3（而且一声不响）
 * </pre>
 *
 * <p>补法是给「捕获了循环变量的那个函数」包一层参数快照：</p>
 *
 * <pre>
 *   fs.push(function () { return i })
 *   →  fs.push((function (i) { return function () { return i } })(i))
 * </pre>
 *
 * <p>每轮调用一次快照函数，参数就是一个新绑定，闭包各自记住当轮的值。只包函数本身、
 * 不动循环结构，所以 {@code break} / {@code continue} / {@code return} 的语义完全不受影响。</p>
 *
 * <p><b>刻意只做「常见形态」，其余一律不动</b>（宁可不改，也不能改错）：</p>
 * <ul>
 *   <li>只认 {@code for (let x = ...; ...; ...)} / {@code for (const x = ...)} 这类 C 风格，
 *       以及 {@code for (let x of ...)} / {@code for (const k in ...)}（支持单个标识符
 *       与简单解构 {@code [a, b]} / {@code {v}} / {@code {k: local}}；带默认值、rest、
 *       嵌套模式、计算属性的一律不动）；</li>
 *   <li>被包的是「正文里引用了循环变量」的<b>函数表达式</b>与<b>能确定边界的箭头函数</b>
 *       （箭头只认块体、或不含运算符的单行表达式体；含运算符、跨行、对象字面量、
 *       {@code async} 的一律不动 —— 宁可少修，也不猜错边界生成语法错误）；</li>
 *   <li><b>不幂等</b>：对已经补偿过的代码再转一次，会再包一层 —— 补偿出来的 IIFE
 *       本身又是一段「引用了循环变量的函数表达式」，会被自己再认出来。语义仍然正确
 *       （多一层冗余包裹而已），而且线上每个脚本只转换一次、没有把产物再喂回来的路径，
 *       所以不影响线上；但「拿转换产物再处理一遍」的工具链要留意
 *       （scan 的幂等项会把它点出来，例子见 12_syntax/14_let_loop_capture.js）。</li>
 *   <li>循环体必须是 {@code {}} 块；<b>嵌套的 {@code for-let} 都会处理</b> ——
 *       扫描时用栈记住「当前词法上真正包住这个位置的循环名」，闭包按这些名字一次性快照。
 *       不能把整条链的名字简单并起来：外层体里、内层循环外的闭包会拿到不存在的变量
 *       （ReferenceError）；</li>
 *   <li>只包 {@code function} <b>表达式</b>；箭头函数（要另保 {@code this}）、
 *       函数声明（是声明，包不了）都不动；</li>
 *   <li>{@code var} 循环头完全不动 —— {@code 3,3,3} 对 {@code var} 才是标准语义；</li>
 *   <li>已经被包住的函数里嵌套的函数不再重复包（外层参数已经把它遮住了）。</li>
 * </ul>
 *
 * <p>只在目标引擎含 Rhino 时启用（GraalJS 的 {@code let} 本来就是标准行为）。</p>
 */
final class ModernJSLetIterationConverter {

    /** 一轮里最多处理的 for 语句数，防止病态输入把扫描拖住。 */
    private static final int MAX_LOOPS = 512;

    private ModernJSLetIterationConverter() {
    }

    /** 全量转换；没有可修的地方就原样返回。 */
    static String convert(String text) {
        if (text == null || text.isEmpty() || !text.contains("for")) {
            return text;
        }
        String masked = ModernJSMask.mask(text);
        StringBuilder out = new StringBuilder();
        // 当前位置上词法可见的 let 循环名。用栈在线推进，天然只包含真正包住该位置的循环 ——
        // 不能把整条嵌套链的名字「并」起来：那样「外层体里、内层循环外」的闭包会被塞进
        // 它根本拿不到的变量，直接 ReferenceError。
        java.util.List<ActiveLoop> active = new java.util.ArrayList<>();
        int copied = 0; // 已复制进 out 的位置
        int search = 0; // 扫描位置 —— 跳过某段时必须先把它复制出去
        int handled = 0;
        while (search < masked.length() && handled < MAX_LOOPS) {
            int loopAt = findLoop(masked, search);
            int fnAt = masked.indexOf("function", search);
            int arrowAt = masked.indexOf("=>", search);
            int next = earliest(loopAt, fnAt, arrowAt);
            if (next < 0) {
                break;
            }
            while (!active.isEmpty() && active.get(active.size() - 1).bodyClose() <= next) {
                active.remove(active.size() - 1); // 已经走出去的循环体
            }
            if (next == loopAt) {
                Loop loop = parseLoop(text, masked, loopAt);
                if (loop == null) {
                    search = loopAt + 3; // 不是我们要处理的形态：只推进扫描位置
                    continue;
                }
                active.add(new ActiveLoop(loop.names, loop.bodyClose));
                search = loop.bodyOpen + 1; // 进到体内继续扫，嵌套循环也在这里被处理
                handled++;
                continue;
            }
            boolean arrow = next == arrowAt;
            if (!arrow && !isFunctionExpression(masked, next)) {
                search = next + "function".length();
                continue;
            }
            int start = arrow ? arrowStart(masked, next) : next;
            int extent = start < 0 ? -1
                    : (arrow ? arrowEnd(masked, next) : functionExtent(masked, next));
            if (start < 0 || extent < 0) {
                search = next + (arrow ? 2 : "function".length()); // 边界拿不准：这个闭包不动
                continue;
            }
            java.util.List<String> used = new java.util.ArrayList<>();
            String span = masked.substring(start, extent);
            for (ActiveLoop loop : active) {
                for (String name : loop.names()) {
                    if (!used.contains(name) && mentionsIdentifier(span, name)) {
                        used.add(name);
                    }
                }
            }
            if (used.isEmpty()) {
                // 不引用循环变量：不包，但继续往里扫（里面还可能有嵌套的 for-let）
                search = next + (arrow ? 2 : "function".length());
                continue;
            }
            out.append(text, copied, start);
            String params = String.join(", ", used);
            out.append("(function (").append(params).append(") { return ");
            out.append(text, start, extent);
            out.append(" })(").append(params).append(")");
            copied = extent;
            // 包住之后，里面的闭包已被参数遮住，不再重复处理。
            // 已知取舍：因此「已包住的闭包内部」的嵌套 for-let 不会下钻，那里的循环变量仍共享。
            // 这很罕见；要修就得包完再进去扫，代价是常见写法（函数里套函数）多一层冗余包裹。
            // 已和作者确认停在这里，不是漏掉。
            search = extent;
        }
        out.append(text, copied, text.length());
        return out.toString();
    }

    /** 一个 C 风格 {@code for (let ...)} 的位置信息。 */
    private record Loop(int headOpen, int headClose, int bodyOpen, int bodyClose, String[] names) {
    }

    /** 找下一个可作语句开头的 {@code for}（只按词边界判断）；没有返回 -1。 */
    private static int findLoop(String masked, int from) {
        for (int i = from; i + 3 <= masked.length(); i++) {
            if (!masked.startsWith("for", i)) {
                continue;
            }
            if (i > 0 && isIdentPart(masked.charAt(i - 1))) {
                continue;
            }
            int after = i + 3;
            if (after < masked.length() && isIdentPart(masked.charAt(after))) {
                continue;
            }
            // 属性访问不算（obj.for）。
            // 这里只做词边界检查，不去猜「语句位置」——注释不是 token，夹在 for 前面完全合法。
            // 之前要求前一个非空字符必须是 ; { }，结果 `/* 注释 */ for (...)`（变形测试插的）
            // 和 `上一行以 ] 结尾、这一行写 for` 都被判成不是 for 语句，整条漏掉。
            if (i > 0 && masked.charAt(i - 1) == '.') {
                continue;
            }
            return i;
        }
        return -1;
    }

    /**
     * 解析 {@code for (...)} 与它的块体；不是「C 风格 + let/const + 块体」一律返回 null。
     */
    private static Loop parseLoop(String text, String masked, int forStart) {
        int open = forStart + 3;
        while (open < masked.length() && Character.isWhitespace(masked.charAt(open))) {
            open++;
        }
        if (open >= masked.length() || masked.charAt(open) != '(') {
            return null;
        }
        int close = ModernJSMask.matchClose(masked, open);
        if (close < 0) {
            return null;
        }
        String head = masked.substring(open + 1, close);
        String headText = text.substring(open + 1, close).trim();
        // 必须是 C 风格（头里有两个顶层分号），且以 let / const 开头
        if (!headText.startsWith("let ") && !headText.startsWith("let\t")
                && !headText.startsWith("const ") && !headText.startsWith("const\t")) {
            return null;
        }
        int semicolons = 0;
        for (int i = 0; i < head.length(); i++) {
            char c = head.charAt(i);
            if (c == ';') {
                semicolons++;
            } else if (c == '(' || c == '[' || c == '{') {
                int end = ModernJSMask.matchClose(head, i);
                if (end < 0) {
                    return null;
                }
                i = end;
            } else if (c == ')' || c == ']' || c == '}') {
                return null;
            }
        }
        String[] names;
        if (semicolons < 2) {
            // for-of / for-in：头里没有顶层分号，形如 `let x of expr` / `const [a, b] of expr`。
            // 标准里它们同样每轮一个新绑定，实测这个 Rhino 分支会共享（读到最后一次的值）。
            names = forOfInNames(head);
            if (names.length == 0) {
                return null;
            }
        } else {
            names = declaredNames(text.substring(open + 1, close));
            if (names.length == 0) {
                return null;
            }
        }
        int bodyOpen = close + 1;
        while (bodyOpen < masked.length() && Character.isWhitespace(masked.charAt(bodyOpen))) {
            bodyOpen++;
        }
        if (bodyOpen >= masked.length() || masked.charAt(bodyOpen) != '{') {
            return null; // 单语句体：不动
        }
        int bodyClose = ModernJSMask.matchClose(masked, bodyOpen);
        if (bodyClose < 0) {
            return null;
        }
        return new Loop(open, close, bodyOpen, bodyClose, names);
    }

    /**
     * 取 {@code for (let x of ...)} / {@code for (const k in ...)} 里被绑定的循环变量名；
     * 不是这种形态返回空数组。
     *
     * <p>传入的 {@code head} 必须是<b>掩码后</b>的头文本，这样 {@code for (const x of "of")}
     * 里字符串中的 {@code of} 不会被当成关键字。解构形态（{@code [a, b]}、{@code {v}}、
     * {@code {k: local}}）也支持，见 {@link #patternNames}。</p>
     */
    private static String[] forOfInNames(String head) {
        String text = head.trim();
        int space = -1;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                space = i;
                break;
            }
        }
        if (space < 0) {
            return new String[0];
        }
        String keyword = text.substring(0, space);
        // 只有 let / const 是「每轮一个新绑定」。var 是函数作用域、本来就该共享，
        // 给它包参数快照会把语义改成 var 不该有的样子 —— 必须排除。
        if (!keyword.equals("let") && !keyword.equals("const")) {
            return new String[0];
        }
        String rest = text.substring(space + 1);
        int depth = 0;
        for (int i = 0; i + 1 < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
                continue;
            }
            if (c == ')' || c == ']' || c == '}') {
                depth--;
                continue;
            }
            if (depth != 0 || (c != 'o' && c != 'i')) {
                continue;
            }
            boolean isOf = rest.startsWith("of", i);
            boolean isIn = rest.startsWith("in", i);
            if (!isOf && !isIn) {
                continue;
            }
            int after = i + 2;
            boolean afterOk = after >= rest.length() || !isIdentPart(rest.charAt(after));
            boolean beforeOk = i == 0 || !isIdentPart(rest.charAt(i - 1));
            if (afterOk && beforeOk) {
                return patternNames(rest.substring(0, i));
            }
        }
        return new String[0];
    }

    /**
     * 从 for-of / for-in 的绑定模式里取出被绑定的名字：{@code x}、{@code [a, b]}、
     * {@code {v}}、{@code {k: local}}。
     *
     * <p>只要有一点拿不准（默认值 {@code =}、rest {@code ...}、嵌套模式、计算属性
     * {@code [k]}、字符串键）就返回<b>空数组</b>，让调用方整条循环不动 —— 猜错模式
     * 会包出引用不到的名字，比不修更糟。</p>
     */
    private static String[] patternNames(String pattern) {
        String text = pattern.trim();
        if (text.isEmpty()) {
            return new String[0];
        }
        if (isIdentifier(text)) {
            return new String[]{text};
        }
        char open = text.charAt(0);
        char close = open == '[' ? ']' : open == '{' ? '}' : 0;
        if (close == 0 || text.charAt(text.length() - 1) != close) {
            return new String[0];
        }
        String inner = text.substring(1, text.length() - 1);
        java.util.List<String> names = new java.util.ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i <= inner.length(); i++) {
            char c = i < inner.length() ? inner.charAt(i) : ',';
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                if (!addPatternItem(names, inner.substring(start, i), open)) {
                    return new String[0];
                }
                start = i + 1;
            }
        }
        return names.toArray(new String[0]);
    }

    /** 解析模式里的一项；拿不准返回 false（调用方放弃整条循环）。 */
    private static boolean addPatternItem(java.util.List<String> names, String raw, char open) {
        String item = raw.trim();
        if (item.isEmpty() || item.indexOf('=') >= 0 || item.contains("...") || item.indexOf('[') >= 0) {
            return false; // 空项 / 默认值 / rest / 计算属性：都不碰
        }
        String bound = item;
        if (open == '{') {
            int colon = item.indexOf(':');
            if (colon >= 0) {
                bound = item.substring(colon + 1).trim();
            }
        }
        if (!isIdentifier(bound)) {
            return false; // 嵌套模式、字符串键等
        }
        if (!names.contains(bound)) {
            names.add(bound); // {a: a} 这种可能重名
        }
        return true;
    }

    /** 取 {@code let a = 1, b = 2} 里的 a、b（只认最简单的「名字 = …」形态）。 */
    private static String[] declaredNames(String head) {
        String text = head.trim();
        int space = text.indexOf(' ');
        if (space < 0) {
            return new String[0];
        }
        String rest = text.substring(space + 1);
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String part : rest.split(",", -1)) {
            String piece = part.trim();
            int eq = piece.indexOf('=');
            String name = (eq >= 0 ? piece.substring(0, eq) : piece).trim();
            if (name.isEmpty() || !isIdentifier(name)) {
                return new String[0]; // 有搞不定的声明符（解构等）：整条不动
            }
            names.add(name);
        }
        return names.toArray(new String[0]);
    }

    /** 一个「当前位置仍然生效」的 let 循环：它的循环变量名，和它体的结束位置。 */
    private record ActiveLoop(String[] names, int bodyClose) {
    }

    /** 取三个下标里最小的非负值；都没有返回 -1。 */
    private static int earliest(int a, int b, int c) {
        int best = -1;
        for (int value : new int[]{a, b, c}) {
            if (value >= 0 && (best < 0 || value < best)) {
                best = value;
            }
        }
        return best;
    }

    // ── 箭头函数的边界 ───────────────────────────────────────────────

    /**
     * 从 {@code =>} 反推箭头函数的起点（形参列表开头）；拿不准返回 -1。
     *
     * <p>{@code async () => …} 会返回 -1：{@code async} 不属于箭头本身，
     * 把它留在外面会变成「对 IIFE 的结果调用 async」，语义完全不同。</p>
     */
    private static int arrowStart(String masked, int arrow) {
        int i = arrow - 1;
        while (i >= 0 && Character.isWhitespace(masked.charAt(i))) {
            i--;
        }
        if (i < 0) {
            return -1;
        }
        if (masked.charAt(i) == ')') {
            int open = matchOpen(masked, i);
            if (open < 0) {
                return -1;
            }
            return isIdentBefore(masked, open) ? -1 : open;
        }
        int last = i;
        while (i >= 0 && isIdentPart(masked.charAt(i))) {
            i--;
        }
        if (i == last) {
            return -1;
        }
        return isIdentBefore(masked, i + 1) ? -1 : i + 1;
    }

    /** 箭头函数体的结束下标（不含）；拿不准返回 -1（宁可不动，也不要猜错边界）。 */
    private static int arrowEnd(String masked, int arrow) {
        int i = arrow + 2;
        while (i < masked.length() && Character.isWhitespace(masked.charAt(i))) {
            i++;
        }
        int bodyStart = i;
        if (i >= masked.length()) {
            return -1;
        }
        if (masked.charAt(i) == '{') {
            int close = ModernJSMask.matchClose(masked, i);
            return close < 0 ? -1 : close + 1;
        }
        int depth = 0;
        while (i < masked.length()) {
            char c = masked.charAt(i);
            if (c == '(' || c == '[') {
                depth++;
            } else if (c == '{') {
                return -1; // 表达式体里的 `{` 只可能是对象字面量，太容易和块混淆
            } else if (c == ')' || c == ']' || c == '}') {
                if (depth == 0) {
                    break; // 到了外层容器的结尾
                }
                depth--;
            } else if ((c == ',' || c == ';' || c == '\n' || c == '\r') && depth == 0) {
                break; // 单行表达式到此为止
            } else if (depth == 0 && isOperatorish(c)) {
                return -1; // `a + b`、`a ? b : c` 之类：不猜
            }
            i++;
        }
        return i > bodyStart ? i : -1;
    }

    /** 从右括号往回找配对的左括号；找不到返回 -1。 */
    private static int matchOpen(String masked, int closeIndex) {
        char clos = masked.charAt(closeIndex);
        char open = clos == ')' ? '(' : clos == ']' ? '[' : clos == '}' ? '{' : 0;
        if (open == 0) {
            return -1;
        }
        int depth = 0;
        for (int i = closeIndex; i >= 0; i--) {
            char c = masked.charAt(i);
            if (c == clos) {
                depth++;
            } else if (c == open && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** {@code at} 左边（跳过空白）是不是标识符字符；用来排除 {@code async} 这类前缀。 */
    private static boolean isIdentBefore(String masked, int at) {
        int i = at - 1;
        while (i >= 0 && Character.isWhitespace(masked.charAt(i))) {
            i--;
        }
        return i >= 0 && isIdentPart(masked.charAt(i));
    }

    /** 一眼看着就像运算符的字符：出现在表达式体里就放弃，不猜边界。 */
    private static boolean isOperatorish(char c) {
        return switch (c) {
            case '+', '-', '*', '/', '%', '<', '>', '=', '!', '&', '|', '^', '~', '?', ':' -> true;
            default -> false;
        };
    }

    /** {@code function} 是不是出现在表达式位置（而不是语句位置的函数声明）。 */
    private static boolean isFunctionExpression(String masked, int at) {
        int prev = at - 1;
        while (prev >= 0 && Character.isWhitespace(masked.charAt(prev))) {
            prev--;
        }
        if (prev < 0) {
            return false;
        }
        char c = masked.charAt(prev);
        if (isIdentPart(c)) {
            String word = wordBefore(masked, prev);
            return switch (word) {
                case "return", "typeof", "in", "of", "new", "delete", "void", "case",
                     "default", "do", "else", "yield" -> true;
                default -> false;
            };
        }
        return switch (c) {
            case '(', ',', '=', ':', '[', '!', '&', '|', '?', '+', '-', '*', '/', '%',
                 '<', '>', '~', '^' -> true;
            default -> false;
        };
    }

    /** 函数表达式的结束下标（不含）；解析不了返回 -1。 */
    private static int functionExtent(String masked, int at) {
        int i = at + "function".length();
        while (i < masked.length() && Character.isWhitespace(masked.charAt(i))) {
            i++;
        }
        while (i < masked.length() && isIdentPart(masked.charAt(i))) {
            i++; // 可选函数名
        }
        while (i < masked.length() && Character.isWhitespace(masked.charAt(i))) {
            i++;
        }
        if (i >= masked.length() || masked.charAt(i) != '(') {
            return -1;
        }
        int close = ModernJSMask.matchClose(masked, i);
        if (close < 0) {
            return -1;
        }
        int brace = close + 1;
        while (brace < masked.length() && Character.isWhitespace(masked.charAt(brace))) {
            brace++;
        }
        if (brace >= masked.length() || masked.charAt(brace) != '{') {
            return -1;
        }
        int bodyClose = ModernJSMask.matchClose(masked, brace);
        return bodyClose < 0 ? -1 : bodyClose + 1;
    }

    // ── 小工具 ────────────────────────────────────────────────────────

    /** 掩码文本里 {@code name} 是否作为标识符出现（前后不是标识符字符）。 */
    private static boolean mentionsIdentifier(String masked, String name) {
        int from = 0;
        while (true) {
            int at = masked.indexOf(name, from);
            if (at < 0) {
                return false;
            }
            from = at + 1;
            boolean beforeOk = at == 0 || !isIdentPart(masked.charAt(at - 1));
            int after = at + name.length();
            boolean afterOk = after >= masked.length() || !isIdentPart(masked.charAt(after));
            if (beforeOk && afterOk) {
                return true;
            }
        }
    }

    private static String wordBefore(String masked, int end) {
        int i = end;
        while (i >= 0 && isIdentPart(masked.charAt(i))) {
            i--;
        }
        return masked.substring(i + 1, end + 1);
    }

    private static boolean isIdentifier(String s) {
        if (s.isEmpty() || !Character.isJavaIdentifierStart(s.charAt(0))) {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
