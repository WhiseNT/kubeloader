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

    // ── 工具 ─────────────────────────────────────────────────────────

    private static void runCase(String name, Runnable body) {
        try {
            body.run();
            passed++;
            System.out.println("[ OK ] " + name);
        } catch (AssertionError e) {
            FAILURES.add(name + " -> " + e.getMessage());
            System.out.println("[FAIL] " + name + " -> " + e.getMessage());
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
