package com.whisent.kubeloader.compat;

import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.Scriptable;

import java.util.ArrayList;
import java.util.List;

/**
 * Rhino 内建补丁（{@link RhinoBuiltinPolyfills}）的回归检查。
 *
 * <p>每一项都在<b>真的 Rhino 上下文</b>里跑：先装上补丁，再执行一段用到该内建的脚本，
 * 断言结果。补丁里的 JS 是手写（还带转义）的，靠这组用例防止把负下标、替换模式、
 * 空位这些细节写错。</p>
 *
 * <p>运行：{@code ./gradlew polyfillRegressionCheck}（已挂在 {@code check} 上）。
 * 不启动游戏，也不需要测试框架。</p>
 */
public final class RhinoPolyfillRegressionCheck {

    private static final ContextFactory CONTEXT_FACTORY = new ContextFactory();

    private static final List<String> FAILURES = new ArrayList<>();
    private static int passed;

    public static void main(String[] args) {
        runCase("globalThis 就是全局对象", RhinoPolyfillRegressionCheck::globalThisIsGlobal);
        runCase("globalThis 上能取到 Math", RhinoPolyfillRegressionCheck::globalThisHasMath);
        runCase("Array.prototype.at 正/负下标", RhinoPolyfillRegressionCheck::arrayAt);
        runCase("Array.prototype.at 越界给 undefined", RhinoPolyfillRegressionCheck::arrayAtOutOfRange);
        runCase("String.prototype.at 正/负下标", RhinoPolyfillRegressionCheck::stringAt);
        runCase("String.prototype.at 越界给 undefined", RhinoPolyfillRegressionCheck::stringAtOutOfRange);
        runCase("Object.hasOwn 真/假", RhinoPolyfillRegressionCheck::objectHasOwn);
        runCase("Object.hasOwn 对 null 抛 TypeError", RhinoPolyfillRegressionCheck::objectHasOwnOnNull);
        runCase("Object.fromEntries（数组）", RhinoPolyfillRegressionCheck::fromEntriesArray);
        runCase("Object.fromEntries（Map）", RhinoPolyfillRegressionCheck::fromEntriesMap);
        runCase("Object.fromEntries（迭代器）", RhinoPolyfillRegressionCheck::fromEntriesIterator);
        runCase("replaceAll（字符串，正则元字符按字面处理）", RhinoPolyfillRegressionCheck::replaceAllString);
        runCase("replaceAll（字符串，$& 替换模式）", RhinoPolyfillRegressionCheck::replaceAllStringWithDollar);
        runCase("replaceAll（全局正则）", RhinoPolyfillRegressionCheck::replaceAllRegex);
        runCase("replaceAll 非全局正则抛 TypeError", RhinoPolyfillRegressionCheck::replaceAllNonGlobalThrows);
        runCase("Array.prototype.flat（默认一层）", RhinoPolyfillRegressionCheck::flatDefault);
        runCase("Array.prototype.flat（Infinity）", RhinoPolyfillRegressionCheck::flatInfinity);
        runCase("Array.prototype.flat 跳过空位", RhinoPolyfillRegressionCheck::flatSkipsHoles);
        runCase("Array.prototype.flatMap", RhinoPolyfillRegressionCheck::flatMap);
        runCase("String.prototype.matchAll（可 for...of）", RhinoPolyfillRegressionCheck::matchAllIterable);
        runCase("String.prototype.matchAll 不改动原正则", RhinoPolyfillRegressionCheck::matchAllKeepsLastIndex);
        runCase("matchAll 非全局正则抛 TypeError", RhinoPolyfillRegressionCheck::matchAllNonGlobalThrows);
        runCase("重复安装安全且不覆盖已有内建", RhinoPolyfillRegressionCheck::installTwiceIsSafe);

        System.out.println();
        if (!FAILURES.isEmpty()) {
            System.out.println("结果：失败 " + FAILURES.size() + " 个，通过 " + passed + " 个");
            FAILURES.forEach(f -> System.out.println("  - " + f));
            throw new AssertionError("Rhino 内建补丁回归检查未通过：" + FAILURES.size() + " 个用例失败");
        }
        System.out.println("结果：全部通过（" + passed + " 个用例）");
    }

    /** 装好补丁后跑一段脚本，返回最后一个表达式的值。 */
    private static String eval(String source) {
        Context cx = CONTEXT_FACTORY.enter();
        Scriptable scope = cx.initStandardObjects();
        RhinoBuiltinPolyfills.install(cx, scope);
        Object result = cx.evaluateString(scope, source, "polyfillRegressionCheck", 1, null);
        return cx.toString(result);
    }

    /** 断言这段脚本会抛异常，且异常里含 expected 片段（多为 TypeError）。 */
    private static void expectThrows(String source, String expected) {
        try {
            String value = eval(source);
            throw new AssertionError("预期抛异常，实际返回：" + value);
        } catch (AssertionError e) {
            throw e;
        } catch (Throwable t) {
            checkTrue(String.valueOf(t).contains(expected), "异常里应包含 " + expected + "，实际：" + t);
        }
    }

    // ── globalThis ────────────────────────────────────────────────────

    private static void globalThisIsGlobal() {
        checkEquals("true", eval("globalThis === this"), "globalThis 应该就是顶层 this");
    }

    private static void globalThisHasMath() {
        checkEquals("true", eval("globalThis.Math === Math"), "globalThis 上应该能取到全局对象的东西");
    }

    // ── at ────────────────────────────────────────────────────────────

    private static void arrayAt() {
        checkEquals("1:3", eval("[1, 2, 3].at(0) + ':' + [1, 2, 3].at(-1)"));
    }

    private static void arrayAtOutOfRange() {
        checkEquals("undefined:undefined",
                eval("typeof [1, 2, 3].at(9) + ':' + typeof [1, 2, 3].at(-9)"));
    }

    private static void stringAt() {
        checkEquals("a:c", eval("'abc'.at(0) + ':' + 'abc'.at(-1)"));
    }

    private static void stringAtOutOfRange() {
        checkEquals("undefined", eval("typeof 'abc'.at(5)"));
    }

    // ── Object.hasOwn / fromEntries ───────────────────────────────────

    private static void objectHasOwn() {
        checkEquals("true:false", eval("Object.hasOwn({a: 1}, 'a') + ':' + Object.hasOwn({a: 1}, 'b')"));
    }

    private static void objectHasOwnOnNull() {
        expectThrows("Object.hasOwn(null, 'a')", "TypeError");
    }

    private static void fromEntriesArray() {
        checkEquals("2", eval("Object.fromEntries([['a', 1], ['b', 2]]).b"));
    }

    private static void fromEntriesMap() {
        checkEquals("9", eval("Object.fromEntries(new Map([['x', 9]])).x"));
    }

    private static void fromEntriesIterator() {
        String source = "function* pairs() { yield ['k', 7]; } Object.fromEntries(pairs()).k;";
        checkEquals("7", eval(source));
    }

    // ── replaceAll ────────────────────────────────────────────────────

    private static void replaceAllString() {
        checkEquals("a-b-c", eval("'a.b.c'.replaceAll('.', '-')"),
                "字符串模式里的 . 必须按字面处理，不能当通配符");
    }

    private static void replaceAllStringWithDollar() {
        checkEquals("[a][a]", eval("'aa'.replaceAll('a', '[$&]')"),
                "字符串模式也要走替换模式（$& 是匹配到的内容）");
    }

    private static void replaceAllRegex() {
        checkEquals("a#b#", eval("'a1b2'.replaceAll(/\\d/g, '#')"));
    }

    private static void replaceAllNonGlobalThrows() {
        expectThrows("'a1'.replaceAll(/\\d/, '#')", "TypeError");
    }

    // ── flat / flatMap ────────────────────────────────────────────────

    private static void flatDefault() {
        checkEquals("3", eval("[[1, [2]], 3].flat().length"), "默认只展平一层");
    }

    private static void flatInfinity() {
        checkEquals("1,2,3", eval("[[1, [2, [3]]]].flat(Infinity).join(',')"));
    }

    private static void flatSkipsHoles() {
        checkEquals("2", eval("var a = [1, , 2]; a.flat().length"), "空位应该被丢掉");
    }

    private static void flatMap() {
        checkEquals("1,10,2,20",
                eval("[1, 2].flatMap(function (x) { return [x, x * 10]; }).join(',')"));
    }

    // ── matchAll ──────────────────────────────────────────────────────

    private static void matchAllIterable() {
        String source = "var out = [];"
                + " for (var m of 'a1b2c'.matchAll(/(\\d)(\\w)/g)) { out.push(m[1] + m[2] + '@' + m.index); }"
                + " out.join(',');";
        checkEquals("1b@1,2c@3", eval(source));
    }

    private static void matchAllKeepsLastIndex() {
        String source = "var re = /(\\d)/g; re.lastIndex = 2;"
                + " var n = 'a1b2'.matchAll(re).length;"
                + " n + ':' + re.lastIndex;";
        checkEquals("2:2", eval(source), "matchAll 不该改动调用方正则的 lastIndex");
    }

    private static void matchAllNonGlobalThrows() {
        expectThrows("'a1'.matchAll(/\\d/)", "TypeError");
    }

    // ── 安装本身的健壮性 ──────────────────────────────────────────────

    private static void installTwiceIsSafe() {
        Context cx = CONTEXT_FACTORY.enter();
        Scriptable scope = cx.initStandardObjects();
        cx.evaluateString(scope, "var beforeEntries = Object.entries; var beforeAt = Array.prototype.at;",
                "polyfillRegressionCheck", 1, null);

        RhinoBuiltinPolyfills.install(cx, scope);
        RhinoBuiltinPolyfills.install(cx, scope); // 第二次应当是无副作用的
        // 引擎本来就有 Object.entries、没有 Array.prototype.at：
        // 前者不该被换掉，后者才该被补上
        String source = "(Object.entries === beforeEntries) + ':'"
                + " + (typeof beforeAt) + ':' + (typeof Array.prototype.at)";
        checkEquals("true:undefined:function",
                cx.toString(cx.evaluateString(scope, source, "polyfillRegressionCheck", 1, null)),
                "重复安装不该覆盖引擎已有的内建，也不该重复定义");
    }

    // ── 工具 ─────────────────────────────────────────────────────────

    private static void runCase(String name, Runnable body) {
        try {
            body.run();
            passed++;
            System.out.println("[ OK ] " + name);
        } catch (Throwable e) {
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
}
