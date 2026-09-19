package com.whisent.kubeloader.scripts;

import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.Scriptable;

import java.util.ArrayList;
import java.util.List;

/**
 * ModernJS 转换（{@link ModernJSParser}）的回归检查，对应 issue #22。
 *
 * <p>背景：类里的静态方法曾被误判成静态字段，转换结果会变成</p>
 * <pre>
 *   A.of(id, count = 1) {
 *       ...
 *   }
 * </pre>
 * <p>这种非法语法，Rhino 报 {@code missing ; before statement}。触发条件是
 * 「静态方法里任意位置出现 '='」——既可能是参数默认值，也可能是方法体内的赋值。</p>
 *
 * <p>另有一层：类方法会被转换成<b>匿名</b> {@code function}，所以默认参数下降
 * 必须覆盖匿名函数，否则 Rhino 报 {@code missing ) after formal parameters}
 * （本项目用的 Rhino fork 不支持默认参数）。</p>
 *
 * <p>运行：{@code ./gradlew parserRegressionCheck}（已挂在 {@code check} 上，
 * 因此 CI 的 build 会跑）。不启动游戏，也不需要测试框架：
 * 只用到转换逻辑 + 项目依赖的 Rhino。</p>
 */
public final class ModernJSParserRegressionCheck {

    /** 与 KubeJS 的用法一致：ContextFactory.enter() 后 evaluateString，见 KubeJSCommands#eval。 */
    private static final ContextFactory CONTEXT_FACTORY = new ContextFactory();

    private static final List<String> FAILURES = new ArrayList<>();
    private static int passed;

    public static void main(String[] args) {
        runCase("静态方法带默认参数（#22 用例 A）", ModernJSParserRegressionCheck::staticMethodWithDefaultParam);
        runCase("静态方法体内含 '='（#22 用例 B）", ModernJSParserRegressionCheck::staticMethodWhoseBodyContainsAssignment);
        runCase("静态方法不含 '='（对照）", ModernJSParserRegressionCheck::staticMethodWithoutEquals);
        runCase("静态字段仍按字段处理", ModernJSParserRegressionCheck::staticField);
        runCase("静态字段的值带括号（回归防护）", ModernJSParserRegressionCheck::staticFieldWithParenthesesInValue);
        runCase("实例方法带默认参数（同类问题的附带修复）", ModernJSParserRegressionCheck::instanceMethodWithDefaultParam);

        // 止血-1：?? 安全网（Rhino 能解析但会算错值，改为显式失败）
        runCase("?? 在代码里会被拦下", ModernJSParserRegressionCheck::nullishIsRejected);
        runCase("?? 在字符串里不该被拦下", ModernJSParserRegressionCheck::nullishInStringIsFine);
        runCase("?? 在注释里不该被拦下", ModernJSParserRegressionCheck::nullishInCommentIsFine);
        runCase("?? 在模板串文本里不该被拦下", ModernJSParserRegressionCheck::nullishInTemplateTextIsFine);
        runCase("?? 在模板串 ${} 代码里要拦下", ModernJSParserRegressionCheck::nullishInTemplateCodeIsRejected);
        runCase("正则里的惰性 ?? 不该被拦下", ModernJSParserRegressionCheck::nullishInRegexIsFine);

        // 止血-2：转换后行号能映射回原始脚本
        runCase("行号映射能指回原始源码", ModernJSParserRegressionCheck::lineMapPointsBackToSource);

        // 止血-4：缺失内建提示 + 解析不了的语法提示
        runCase("GraalJS 目标下不该拦 ??", ModernJSParserRegressionCheck::nullishAllowedForGraalJsTarget);
        runCase("缺失的内建会被提示（带原始行号）", ModernJSParserRegressionCheck::missingBuiltinsAreReported);
        runCase("字符串/注释/正则里的名字不算使用", ModernJSParserRegressionCheck::missingBuiltinsInTextAreIgnored);
        runCase("解析不了的语法会给出改写建议", ModernJSParserRegressionCheck::unsupportedSyntaxGetsAdvice);

        // 止血-8：`...` 展开 / 剩余参数
        runCase("`...` 各种写法都能转换", ModernJSParserRegressionCheck::spreadFormsAreConverted);

        // 止血-5：类体花括号按词法数 + 转换失败也要变成可读错误
        runCase("类体字符串里的 } 不算类结束", ModernJSParserRegressionCheck::bracesInsideStringDoNotEndClass);
        runCase("类体注释里的 } 不算类结束", ModernJSParserRegressionCheck::bracesInsideCommentDoNotEndClass);
        runCase("类体模板串里的 } 不算类结束", ModernJSParserRegressionCheck::bracesInsideTemplateDoNotEndClass);
        runCase("getter 体里字符串的 } 不截断", ModernJSParserRegressionCheck::getterBodyWithBraceInString);
        runCase("同行/嵌入式 class 都能转换", ModernJSParserRegressionCheck::inlineClassFormsWork);
        runCase("未闭合的 class 给出建议", ModernJSParserRegressionCheck::unclosedClassGivesAdvice);

        System.out.println();
        if (!FAILURES.isEmpty()) {
            System.out.println("结果：失败 " + FAILURES.size() + " 个，通过 " + passed + " 个");
            FAILURES.forEach(f -> System.out.println("  - " + f));
            throw new AssertionError("ModernJSParser 回归检查未通过：" + FAILURES.size() + " 个用例失败");
        }
        System.out.println("结果：全部通过（" + passed + " 个用例）");
    }

    // ── issue #22 的复现用例 ───────────────────────────────────────────

    private static void staticMethodWithDefaultParam() {
        String source = lines(
                "class A {",
                "    constructor(id, count) {",
                "        this._id = id",
                "        this._count = count",
                "    }",
                "    static of(id, count = 1) {",
                "        return new A(id, count)",
                "    }",
                "}",
                "let a = A.of(\"abc\")",
                "a._count;");

        String out = ModernJSParser.parse(source);
        checkTrue(out.contains("A.of = function(id, count) {"), "静态方法未被识别为方法：" + out);
        checkTrue(out.contains("count = count === undefined ? 1 : count;"), "默认值未下沉进函数体：" + out);
        checkTrue(!out.contains("A.of(id, count"), "仍输出了非法语法：" + out);
        checkEquals("1", evalTransformed(source), "转换后交给 Rhino 执行，count 应默认为 1");
    }

    private static void staticMethodWhoseBodyContainsAssignment() {
        String source = lines(
                "class B {",
                "    constructor(id, count) {",
                "        this._id = id",
                "        this._count = count",
                "    }",
                "    static of(id, count) {",
                "        if (!count) {",
                "            count = 1",
                "        }",
                "        return new B(id, count)",
                "    }",
                "}",
                "let b = B.of(\"abc\")",
                "b._count;");

        String out = ModernJSParser.parse(source);
        checkTrue(out.contains("B.of = function(id, count) {"), "静态方法未被识别为方法：" + out);
        checkTrue(!out.contains("B.of(id, count) {"), "仍输出了非法语法：" + out);
        checkEquals("1", evalTransformed(source), "转换后交给 Rhino 执行");
    }

    // ── 对照与回归防护 ────────────────────────────────────────────────

    private static void staticMethodWithoutEquals() {
        String source = lines(
                "class C {",
                "    constructor(id) {",
                "        this._id = id",
                "    }",
                "    static make(id) {",
                "        return new C(id)",
                "    }",
                "}",
                "let c = C.make(7)",
                "c._id;");

        String out = ModernJSParser.parse(source);
        checkTrue(out.contains("C.make = function(id) {"), out);
        checkEquals("7", evalTransformed(source));
    }

    private static void staticField() {
        String source = lines(
                "class D {",
                "    static DEFAULT = 5",
                "    static make(id) {",
                "        return id",
                "    }",
                "}",
                "D.DEFAULT;");

        String out = ModernJSParser.parse(source);
        checkTrue(out.contains("D.DEFAULT = 5"), "静态字段未被按字段处理：" + out);
        checkTrue(out.contains("D.make = function(id) {"), out);
        checkEquals("5", evalTransformed(source));
    }

    private static void staticFieldWithParenthesesInValue() {
        String source = lines(
                "class E {",
                "    static BASE = Math.max(1, 2)",
                "    static make(id) {",
                "        return id",
                "    }",
                "}",
                "E.BASE;");

        String out = ModernJSParser.parse(source);
        checkTrue(out.contains("E.BASE = Math.max(1, 2)"), "静态字段未被按字段处理：" + out);
        checkTrue(!out.contains("E.BASE = function"), "静态字段被误判成方法：" + out);
        checkTrue(out.contains("E.make = function(id) {"), out);
        checkEquals("2", evalTransformed(source));
    }

    private static void instanceMethodWithDefaultParam() {
        String source = lines(
                "class F {",
                "    constructor(id) {",
                "        this._id = id",
                "    }",
                "    describe(prefix = \"id\") {",
                "        return prefix + this._id",
                "    }",
                "}",
                "let f = new F(9)",
                "f.describe();");

        String out = ModernJSParser.parse(source);
        checkTrue(out.contains("F.prototype.describe = function(prefix) {"), out);
        checkTrue(out.contains("prefix = prefix === undefined ? \"id\" : prefix;"), "默认值未下沉进函数体：" + out);
        checkEquals("id9", evalTransformed(source));
    }

    // ── 止血-1：?? 安全网（Rhino 能解析却会算错值，改为显式失败） ─────────

    private static void nullishIsRejected() {
        expectRejected(lines(
                "let a = null;",
                "let b = a ?? 1;"), 2);
    }

    private static void nullishInStringIsFine() {
        expectAccepted(lines("let s = \"a ?? b\";"));
    }

    private static void nullishInCommentIsFine() {
        expectAccepted(lines("// a ?? b", "let x = 1;"));
    }

    private static void nullishInTemplateTextIsFine() {
        expectAccepted(lines("let s = `a ?? b`;"));
    }

    private static void nullishInTemplateCodeIsRejected() {
        expectRejected(lines("let t = `${a ?? 1}`;"), 1);
    }

    private static void nullishInRegexIsFine() {
        expectAccepted(lines("let re = /a??/;", "let s = 'x';"));
    }

    /** 断言这段代码会被安全网拦下，并且行号指向原始源码。 */
    private static void expectRejected(String source, int expectedLine) {
        try {
            ModernJSParser.parse(source);
        } catch (ModernJSParseException e) {
            checkEquals(String.valueOf(expectedLine), String.valueOf(e.getLine()), "拦截行号不符");
            checkTrue(e.getSourceLine() != null && !e.getSourceLine().isEmpty(), "应带上原始行内容");
            return;
        }
        throw new AssertionError("预期被安全网拦下，但没有报错");
    }

    /** 断言这段代码能正常通过（不该被误报）。 */
    private static void expectAccepted(String source) {
        try {
            ModernJSParser.parse(source);
        } catch (ModernJSParseException e) {
            throw new AssertionError("不该被拦下，但被误报了：" + e.describe());
        }
    }

    // ── 止血-2：转换后行号 → 原始源码行号 ──────────────────────────────

    private static void lineMapPointsBackToSource() {
        String original = lines(
                "let before = 1;",
                "class A {",
                "    constructor(id) {",
                "        this._id = id",
                "    }",
                "}",
                "let after = 2;");
        String generated = ModernJSParser.parse(original);
        String[] genLines = generated.split("\\r?\\n", -1);

        // 类体收尾的 "}" 不能和下一行粘在一起（ASI 隐患 + 会让行号错位）
        checkTrue(generated.contains("\nlet after = 2;"), "类之后的语句被粘住了：" + generated);

        // 类之后的语句是原样保留的，必须精确映回原始第 7 行
        int afterIndex = indexOfGeneratedLine(genLines, "let after = 2;");
        checkEquals("7", String.valueOf(OriginalLineMapper.toOriginalLine(generated, original, afterIndex)),
                "类之后的语句应精确映回原始第 7 行");

        // 类被展开成的新行，应映射回类头附近（2~6 行之间）
        int mapped = OriginalLineMapper.toOriginalLine(generated, original, 2);
        checkTrue(mapped >= 2 && mapped <= 6, "类体内生成行的映射落在预期范围外：" + mapped);

        // 行内容也能取到
        checkTrue(!OriginalLineMapper.lineText(original, mapped).isEmpty(), "应能取到原始行内容");
    }

    /** 在转换后代码里找某一行（1 起），找不到直接失败。 */
    private static int indexOfGeneratedLine(String[] lines, String text) {
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().equals(text)) return i + 1;
        }
        throw new AssertionError("转换后代码里找不到这一行：" + text);
    }

    // ── 止血-4：缺失内建提示 + 解析不了的语法提示 ───────────────────────

    private static void nullishAllowedForGraalJsTarget() {
        // GraalJS 原生支持 ??，对它做「静默算错」的拦截属于误伤
        ModernJSParser.parse(lines("let a = null;", "let b = a ?? 1;"), false);
    }

    private static void missingBuiltinsAreReported() {
        String source = lines(
                "let p = new Promise(function (r) { r(1) });", // 1
                "let g = globalThis;",                          // 2
                "let v = [1, 2].at(-1);",                       // 3
                "let h = Object.hasOwn({}, \"a\");");           // 4

        String joined = String.join(" | ", ModernJSSyntaxGuard.collectWarnings(source));
        checkTrue(joined.contains("Promise"), "应提示 Promise：" + joined);
        checkTrue(joined.contains("globalThis"), "应提示 globalThis：" + joined);
        checkTrue(joined.contains("Array/String.prototype.at"), "应提示 .at()：" + joined);
        checkTrue(joined.contains("Object.hasOwn"), "应提示 Object.hasOwn：" + joined);
        checkTrue(joined.contains("第 3 行"), "提示里应带上原始行号：" + joined);
    }

    private static void missingBuiltinsInTextAreIgnored() {
        String source = lines(
                "// Promise 只是注释",
                "let s = \"Promise and Proxy\";",
                "let r = /Promise/;");

        int count = ModernJSSyntaxGuard.collectWarnings(source).size();
        checkEquals("0", String.valueOf(count), "字符串/注释/正则里的名字不该被当成使用");
    }

    private static void unsupportedSyntaxGetsAdvice() {
        String async = ModernJSSyntaxGuard.explainUnsupported("async function f() { return 1; }");
        checkTrue(async != null && async.contains("async"), "应识别 async/await：" + async);

        String logicalAssign = ModernJSSyntaxGuard.explainUnsupported("var a = 1; a ||= 2;");
        checkTrue(logicalAssign != null && logicalAssign.contains("逻辑赋值"),
                "应识别逻辑赋值：" + logicalAssign);

        checkTrue(ModernJSSyntaxGuard.explainUnsupported("let a = [1]; let b = [...a];") == null,
                "`...` 已经能转换，不该再提示「不支持」");

        checkTrue(ModernJSSyntaxGuard.explainUnsupported("let a = 1;") == null,
                "普通代码不该给出建议");
    }

    // ── 止血-8：`...`（展开 / 剩余参数）─────────────────────────────────

    private static void spreadFormsAreConverted() {
        checkEquals("1,2,3", evalTransformed(lines(
                "var b = [2, 3];",
                "var a = [1, ...b];",
                "a.join(',');")), "数组字面量展开");

        checkEquals("6", evalTransformed(lines(
                "function sum(x, y, z) { return x + y + z; }",
                "var a = [1, 2, 3];",
                "sum(...a);")), "函数调用展开");

        checkEquals("3", evalTransformed(lines(
                "var o = { v: 0, add: function (a, b) { return this.v + a + b; } };",
                "o.add(...[1, 2]);")), "成员调用展开要保留 this");

        checkEquals("1|2,3", evalTransformed(lines(
                "function f(a, ...rest) { return a + '|' + rest.join(','); }",
                "f(1, 2, 3);")), "剩余参数");

        checkEquals("3", evalTransformed(lines(
                "function P(x, y) { this.s = x + y; }",
                "new P(...[1, 2]).s;")), "构造调用展开");

        checkEquals("0,a,b", evalTransformed(lines(
                "var a = [0, ...'ab'];",
                "a.join(',');")), "字符串展开按字符拆开");

        checkEquals("3", evalTransformed(lines(
                "var a = { x: 1 };",
                "var b = {...a, y: 2};",
                "b.x + b.y;")), "对象字面量展开");

        // 展开的东西出现在字符串/注释里时不该被当成代码
        checkEquals("true", evalTransformed(lines(
                "var s = \"...a\";",
                "var t = `...b`;",
                "s === '...a' && t === '...b';")), "字符串/模板串里的 ... 不该被改写");
    }

    // ── 止血-5：类体花括号必须按词法数；转换失败的异常也必须被接住 ────────
    //
    // 之前是裸字符循环，"}" 只要出现在字符串/注释/模板里就会被当成类结束，
    // 类体被截断后粘出非法语句。这类输入很常见（return "}"、/\* } \*/、`}`）。

    private static void bracesInsideStringDoNotEndClass() {
        String source = lines(
                "class A {",
                "    m() {",
                "        return \"}\";",
                "    }",
                "}",
                "new A().m();");
        checkEquals("}", evalTransformed(source), "类体里字符串的 \"}\" 不该被当成类结束");
    }

    private static void bracesInsideCommentDoNotEndClass() {
        String source = lines(
                "class A {",
                "    /* } */",
                "    m() { return 5 }",
                "}",
                "new A().m();");
        checkEquals("5", evalTransformed(source), "块注释里的 } 不该被算进配对");
    }

    private static void bracesInsideTemplateDoNotEndClass() {
        String source = lines(
                "class A {",
                "    m() {",
                "        return `}`;",
                "    }",
                "}",
                "new A().m();");
        checkEquals("}", evalTransformed(source), "模板串文本里的 } 不该被算进配对");
    }

    private static void getterBodyWithBraceInString() {
        String source = lines(
                "class A {",
                "    get x() { return \"}\" }",
                "}",
                "new A().x;");
        checkEquals("}", evalTransformed(source), "getter 体里的 \"}\" 不该截断 getter 体");
    }

    private static void inlineClassFormsWork() {
        // 以前 class 必须独占一行；现在与其它代码同行、嵌套在方法体里都能转
        checkEquals("5", evalTransformed("class A { x = 5; get() { return this.x } } new A().get();"),
                "同行写的字段 + 方法应能正确转换");

        checkEquals("3", evalTransformed("class A { m() { return 1 } } class B { m() { return 2 } } new A().m() + new B().m();"),
                "同一行两个 class 应能正确转换");

        checkEquals("7", evalTransformed(lines(
                "function make() {",
                "    class A {",
                "        m() { return 7 }",
                "    }",
                "    return new A().m();",
                "}",
                "make();")), "函数体（与 function 同一行）里的 class 应能正确转换");

        checkEquals("true", evalTransformed(
                "class Outer { m() { class Inner { } return new Inner() instanceof Object } } new Outer().m();"),
                "嵌套在方法体里的 class 应能正确转换");
    }

    private static void unclosedClassGivesAdvice() {
        try {
            ModernJSParser.parse(lines("class A {", "    m() { return 1 }"));
        } catch (ModernJSParseException e) {
            checkTrue(e.describe().contains("配对"), "应提示花括号不配对：" + e.describe());
            return;
        } catch (RuntimeException e) {
            throw new AssertionError("不该抛出非 ModernJSParseException 的异常：" + e);
        }
        throw new AssertionError("预期未闭合的 class 会被拦下，但通过了");
    }

    // ── 工具 ─────────────────────────────────────────────────────────

    private static void runCase(String name, Runnable body) {
        try {
            body.run();
            passed++;
            System.out.println("[ OK ] " + name);
        } catch (Throwable e) {
            // 用例里可能直接让 Rhino 跑代码，会抛非 AssertionError 的异常；
            // 这里一并接住，否则一个用例会把整轮检查打断
            String detail = e instanceof AssertionError ? String.valueOf(e.getMessage()) : String.valueOf(e);
            FAILURES.add(name + " -> " + detail);
            System.out.println("[FAIL] " + name + " -> " + detail);
        }
    }

    private static void checkTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void checkEquals(String expected, String actual) {
        checkEquals(expected, actual, "值不匹配");
    }

    private static void checkEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + "（期望 " + expected + "，实际 " + actual + "）");
        }
    }

    private static String lines(String... parts) {
        return String.join("\n", parts);
    }

    /** 转换后交给 Rhino 真跑一遍，返回最后一个表达式的值（字符串形式）。 */
    private static String evalTransformed(String source) {
        Context cx = CONTEXT_FACTORY.enter();
        Scriptable scope = cx.initStandardObjects();
        Object result = cx.evaluateString(scope, ModernJSParser.parse(source), "parserRegressionCheck", 1, null);
        return cx.toString(result);
    }
}
