package com.whisent.kubeloader.scripts;

import com.whisent.kubeloader.compat.RhinoBuiltinPolyfills;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.Scriptable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * ModernJS 示例语料库的健壮性检查：把 {@code examples/modernjs/} 下每个 {@code .js}
 * 脚本都按<b>线上同一条链路</b>真跑一遍，并断言脚本自己写下的期望值。
 *
 * <p>链路与 {@code KLScriptLoader} 保持一致，任何一环不同都可能放过真问题：</p>
 * <ol>
 *   <li>{@link ModernJSParser#parse(String)} —— 转换（含 {@code ??} 之类的硬失败）；</li>
 *   <li>Rhino 自己的 {@code Parser} 校验转换结果 —— 线上也是先校验再执行；</li>
 *   <li>装好 Rhino 内建补丁后 {@code evaluateString} —— 与线上 mixin 装的补丁一致。</li>
 * </ol>
 *
 * <p>脚本可以用三个约定向检查器声明自己的契约（写在注释里即可）：</p>
 * <ul>
 *   <li>{@code // expect-error: 片段} —— 这个脚本必须<b>响亮失败</b>，且报错含该片段。
 *       用来钉住「不支持就该报错，而不是静默算错」的行为。</li>
 *   <li>{@code // requires-transform} —— 原文必须<b>跑不通</b>。用来防止写出「不走转换
 *       也能过」的假用例：如果原始写法本来就能跑，那这个脚本什么也没验证到。</li>
 *   <li>其余脚本：必须跑通，且里面的 {@code __check} 全部成立。</li>
 * </ul>
 *
 * <p>运行：{@code ./gradlew modernJSCorpusCheck}（已挂在 {@code check} 上）。
 * 加 {@code -Dmodernjs.verbose=true} 可打印脚本里的 {@code console} 输出。</p>
 */
public final class ModernJSScriptCorpusCheck {

    private static final ContextFactory CONTEXT_FACTORY = new ContextFactory();

    /** 语料根目录：优先用系统属性，否则从工作目录往上找 {@code examples/modernjs}。 */
    private static final String CORPUS_PROPERTY = "modernjs.corpus";
    private static final String DEFAULT_CORPUS = "examples/modernjs";

    private static final boolean VERBOSE = Boolean.getBoolean("modernjs.verbose");

    /** {@code // expect-error: xxx} */
    private static final Pattern EXPECT_ERROR =
            Pattern.compile("^\\s*//\\s*expect-error\\s*:?\\s*(.*)$", Pattern.MULTILINE);
    private static final Pattern REQUIRES_TRANSFORM =
            Pattern.compile("^\\s*//\\s*requires-transform\\b", Pattern.MULTILINE);

    /** 断言与记录用的辅助函数。刻意用 ES5 写法：它自己也要过 Rhino，不能依赖被检查的转换器。 */
    private static final String PRELUDE = """
            var __count = 0;
            var __failures = [];
            var __logs = [];

            function __str(v) {
                if (v === null) return 'null';
                if (v === undefined) return 'undefined';
                return String(v);
            }

            // 断言实际值与期望值相等（都按字符串比，脚本自己负责把复杂值拼成字符串）
            function __check(name, actual, expected) {
                __count = __count + 1;
                var a = __str(actual);
                var e = __str(expected);
                if (a !== e) {
                    __failures.push(name + '：期望 ' + e + '，实际 ' + a);
                }
            }

            function __checkTrue(name, cond) {
                __count = __count + 1;
                if (!cond) {
                    __failures.push(name + '：期望真值，实际 ' + __str(cond));
                }
            }

            function __checkThrows(name, fn) {
                __count = __count + 1;
                var threw = false;
                try {
                    fn();
                } catch (e) {
                    threw = true;
                }
                if (!threw) {
                    __failures.push(name + '：本应抛异常，但没有');
                }
            }

            // 打包回传：断言数 \0 失败数 \0 失败详情 \0 日志
            function __report() {
                var NUL = String.fromCharCode(0);
                var SEP = String.fromCharCode(1);
                return __count + NUL + __failures.length + NUL
                        + __failures.join(SEP) + NUL + __logs.join(SEP);
            }

            // 脚本里可以照常写 console.log —— 记下来，-Dmodernjs.verbose=true 时打印
            function __logger(kind) {
                return function () {
                    __logs.push(kind + Array.prototype.join.call(arguments, ' '));
                };
            }

            var console = {
                log: __logger('[log]'),
                info: __logger('[info]'),
                warn: __logger('[warn]'),
                error: __logger('[error]'),
                debug: __logger('[debug]')
            };
            """;

    private static final List<String> FAILURES = new ArrayList<>();
    private static int fileCount;
    private static int assertionCount;
    private static int rejectCount;
    private static final Map<String, int[]> PER_CATEGORY = new TreeMap<>();

    public static void main(String[] args) throws IOException {
        Path root = locateCorpus();
        List<Path> scripts = collect(root);
        if (scripts.isEmpty()) {
            throw new AssertionError("没在 " + root + " 下找到任何 .js 脚本");
        }
        System.out.println("ModernJS 示例语料库：" + root);
        System.out.println("共 " + scripts.size() + " 个脚本");
        System.out.println();

        for (Path script : scripts) {
            checkOne(root, script);
        }

        System.out.println();
        System.out.println("分类统计：");
        PER_CATEGORY.forEach((category, stat) -> System.out.printf(
                "  %-16s 脚本 %2d 个，通过 %2d 个，断言 %3d 条%n",
                category, stat[0], stat[1], stat[2]));

        System.out.println();
        if (!FAILURES.isEmpty()) {
            System.out.println("结果：失败 " + FAILURES.size() + " 个，通过 "
                    + (fileCount - FAILURES.size()) + " 个");
            FAILURES.forEach(f -> System.out.println("  - " + f));
            throw new AssertionError("ModernJS 示例语料库检查未通过：" + FAILURES.size() + " 个脚本失败");
        }
        System.out.println("结果：全部通过（" + fileCount + " 个脚本，"
                + assertionCount + " 条断言，其中 " + rejectCount + " 个脚本按预期失败）");
    }

    // ── 单个脚本 ──────────────────────────────────────────────────────

    private static void checkOne(Path root, Path script) {
        String relative = root.relativize(script).toString().replace('\\', '/');
        String category = relative.contains("/") ? relative.substring(0, relative.indexOf('/')) : ".";
        int[] stat = PER_CATEGORY.computeIfAbsent(category, k -> new int[3]);
        stat[0]++;
        fileCount++;

        String source;
        try {
            source = Files.readString(script, StandardCharsets.UTF_8);
        } catch (IOException e) {
            fail(relative, stat, "读文件失败：" + e);
            return;
        }

        boolean inRejectDir = category.equals("07_reject");
        Matcher expectMatcher = EXPECT_ERROR.matcher(source);
        boolean expectsError = inRejectDir || expectMatcher.find();
        String expectFragment = expectsError && expectMatcher.reset().find()
                ? expectMatcher.group(1).trim() : "";

        if (expectsError) {
            checkExpectedError(relative, stat, source, expectFragment);
            return;
        }
        if (REQUIRES_TRANSFORM.matcher(source).find()) {
            checkRawCannotRun(relative, stat, source);
        }
        checkScript(relative, stat, source);
    }

    /** 普通脚本：转换 → 校验 → 执行 → 看断言。 */
    private static void checkScript(String name, int[] stat, String source) {
        Context cx = CONTEXT_FACTORY.enter();
        Scriptable scope = newScope(cx);
        List<String> details = new ArrayList<>();
        String thrown = null;
        try {
            String transformed = ModernJSParser.parse(source);
            // 线上也是先过 Rhino 的 parser 再执行，这里保持同一步骤
            new dev.latvian.mods.rhino.Parser(cx).parse(transformed, name, 0);
            cx.evaluateString(scope, transformed, name, 1, null);
        } catch (Throwable t) {
            thrown = describe(t);
        }

        // 不管有没有抛异常，都把脚本已经记下的断言失败读出来：脚本中途抛异常时，
        // 那之前的断言失败同样要报，否则会被异常盖掉（只看到异常、看不到真正的错值）。
        int assertions = 0;
        try {
            String[] report = readReport(cx, scope);
            assertions = parseInt(report[0]);
            if (!report[2].isEmpty()) {
                details.addAll(List.of(report[2].split(String.valueOf((char) 1))));
            }
            if (VERBOSE) {
                printLogs(report[3]);
            }
        } catch (Throwable ignored) {
            // 连 prelude 都没跑到（比如转换阶段就失败），没有报告可读
        }
        stat[2] += assertions;
        assertionCount += assertions;

        if (thrown != null) {
            details.add(0, "执行失败：" + thrown);
        }
        if (!details.isEmpty()) {
            details.forEach(detail -> fail(name, stat, detail));
            return;
        }
        stat[1]++;
        System.out.printf("[ OK ] %-46s %2d 条断言%n", name, assertions);
    }

    /** expect-error 脚本：必须失败，且报错要含声明的片段。 */
    private static void checkExpectedError(String name, int[] stat, String source, String fragment) {
        Context cx = CONTEXT_FACTORY.enter();
        String message = null;
        try {
            Scriptable scope = newScope(cx);
            String transformed = ModernJSParser.parse(source);
            new dev.latvian.mods.rhino.Parser(cx).parse(transformed, name, 0);
            cx.evaluateString(scope, transformed, name, 1, null);
        } catch (Throwable t) {
            message = describe(t);
        }
        if (message == null) {
            fail(name, stat, "这个写法本应被拒绝（响亮失败），但一路跑通了——静默算错的风险");
            return;
        }
        if (!fragment.isEmpty() && !message.contains(fragment)) {
            fail(name, stat, "报错里应含「" + fragment + "」，实际是：" + message);
            return;
        }
        stat[1]++;
        rejectCount++;
        System.out.printf("[ OK ] %-46s 按预期报错：%s%n", name, shorten(message, 60));
    }

    /** requires-transform 脚本：原文必须跑不通，否则这个脚本没验证到转换器。 */
    private static void checkRawCannotRun(String name, int[] stat, String source) {
        Context cx = CONTEXT_FACTORY.enter();
        String message = null;
        try {
            Scriptable scope = newScope(cx);
            new dev.latvian.mods.rhino.Parser(cx).parse(source, name + "(原文)", 0);
            cx.evaluateString(scope, source, name + "(原文)", 1, null);
        } catch (Throwable t) {
            message = describe(t);
        }
        if (message == null) {
            fail(name, stat, "标了 requires-transform，可原文自己就能跑通——这个脚本没验证到转换器");
        }
    }

    private static Scriptable newScope(Context cx) {
        Scriptable scope = cx.initStandardObjects();
        RhinoBuiltinPolyfills.install(cx, scope);
        cx.evaluateString(scope, PRELUDE, "<prelude>", 1, null);
        return scope;
    }

    private static String[] readReport(Context cx, Scriptable scope) {
        Object report = cx.evaluateString(scope, "__report()", "<report>", 1, null);
        String text = cx.toString(report);
        String[] parts = text.split(String.valueOf((char) 0), -1);
        return parts.length >= 4 ? parts : new String[]{parts[0], "0", "", ""};
    }

    private static void printLogs(String logs) {
        if (logs.isEmpty()) {
            return;
        }
        for (String line : logs.split(String.valueOf((char) 1))) {
            System.out.println("         " + line);
        }
    }

    // ── 语料定位与遍历 ────────────────────────────────────────────────

    private static Path locateCorpus() {
        String configured = System.getProperty(CORPUS_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            Path path = Paths.get(configured).toAbsolutePath().normalize();
            if (!Files.isDirectory(path)) {
                throw new AssertionError("系统属性 " + CORPUS_PROPERTY
                        + " 指向的目录不存在：" + path);
            }
            return path;
        }
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve(DEFAULT_CORPUS);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new AssertionError("找不到语料目录 " + DEFAULT_CORPUS
                + "，可用 -D" + CORPUS_PROPERTY + "=<路径> 指定");
    }

    private static List<Path> collect(Path root) throws IOException {
        List<Path> scripts = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".js"))
                    .forEach(scripts::add);
        }
        scripts.sort((a, b) -> a.toString().compareTo(b.toString()));
        return Collections.unmodifiableList(scripts);
    }

    // ── 小工具 ────────────────────────────────────────────────────────

    private static void fail(String name, int[] stat, String detail) {
        FAILURES.add(name + " -> " + detail);
        System.out.printf("[FAIL] %-46s %s%n", name, detail);
    }

    private static int parseInt(String text) {
        try {
            return (int) Double.parseDouble(text.trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static String describe(Throwable t) {
        String message = String.valueOf(t.getMessage()).replace('\n', ' ').trim();
        return t.getClass().getSimpleName() + (message.isEmpty() ? "" : ": " + message);
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
