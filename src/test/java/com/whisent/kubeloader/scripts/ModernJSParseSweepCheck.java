package com.whisent.kubeloader.scripts;

import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.ContextFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.stream.Stream;

/**
 * ModernJS「转换结果是否合法」扫描。
 *
 * <p>判据只有一条，且和线上完全一致：<b>转换结果必须能被 Rhino 的 parser 解析</b>。
 * 线上 {@code KLScriptLoader} 就是这么做的 —— 转换之后 {@code new Parser(cx).parse(...)}
 * 校验一遍，过了才执行。这里把同一件事批量做，但<b>不执行</b>、不需要脚本里写任何断言，
 * 因此可以直接指向任意外部语料目录：</p>
 *
 * <pre>
 * ./gradlew modernJSSweepCheck                              # 默认 examples/modernjs
 * ./gradlew modernJSSweepCheck -Pcorpus=&lt;目录&gt;              # 指向外部语料
 * ./gradlew modernJSSweepCheck -Pcorpus=&lt;目录&gt; -Pbaseline=&lt;文件&gt;
 * </pre>
 *
 * <p>每个文件归入以下之一：</p>
 * <ul>
 *   <li><b>通过</b> —— 转换成功，且结果过得了 Rhino parser。</li>
 *   <li><b>响亮失败</b> —— 预期内的失败，有两种到达方式：转换阶段抛
 *       {@link ModernJSParseException}（{@code ??}、不支持的解构形式…），
 *       或转换成功但结果过不了 Rhino parser（线上同样是「跳过这个脚本 + 报错」）。
 *       语料里用 {@code // expect-error: 片段} 或放在 {@code 07_reject/} 下声明。</li>
 *   <li><b>转换器崩溃</b> —— 抛 {@link ModernJSParseException} 但<b>带 cause</b>：
 *       转换器自己崩了（NPE、下标越界…）。这是 bug，不算「预期内的失败」。</li>
 *   <li><b>输出不合法</b> —— 转换成功，但结果过不了 Rhino parser，且这个文件没声明预期失败。
 *       其中「原文本来能过 parser、结果却过不了」的算<b>净回归</b>，优先展示。</li>
 *   <li><b>与声明的预期不符</b> —— 声明了必须响亮失败，却一路通过（静默接受了一个
 *       处理不了的写法），或报错里不含 {@code expect-error} 声明的片段。</li>
 * </ul>
 *
 * <p><b>要紧的语义：外部语料上的「响亮失败」不等于通过。</b>它只说明「转换器明确拒绝了
 * 这个写法」（或线上会拦住它），不代表「这个写法被支持」。所以拒绝的原因同样会聚合打印，
 * 供人判断覆盖率。</p>
 *
 * <p>关于 {@code expect-error}：语料里它表示「整条链路（含运行期）必须失败」，而本扫描
 * <b>不执行</b>脚本，验不了运行期。所以只有 {@code 07_reject/} 目录（表示「parse 层就该被
 * 拒绝」）会被当成预期失败来核对，单独的 {@code expect-error} 注释在扫描里不参与判定。</p>
 *
 * <p>失败诊断与线上对齐（可以直接照抄给用户）：Rhino 报错 + 转换后行号映射回原文行
 * + 原文该行内容 + {@link ModernJSSyntaxGuard#explainUnsupported} 的改写建议
 * + 「结果里残留 class / 残留带默认值的解构模式」的提示。</p>
 *
 * <p>大语料下不刷屏：逐分类打点、失败按症状聚合且每类只打印前 {@code modernjs.sweep.show}
 * 条（默认 5），完整明细落到 {@code build/modernjs-sweep-report.txt}。已知失败可以列进
 * baseline 文件（一行一个相对路径，{@code #} 开头是注释）而不判失败——那是给外部语料准备的，
 * 那里必然有一批故意写坏的负向用例。</p>
 */
public final class ModernJSParseSweepCheck {

    private static final ContextFactory CONTEXT_FACTORY = new ContextFactory();

    private static final String BASELINE_PROPERTY = "modernjs.sweep.baseline";
    private static final int SHOW_PER_SYMPTOM = intProperty("modernjs.sweep.show", 5);
    private static final boolean VERBOSE = Boolean.getBoolean("modernjs.sweep.verbose");

    /** 本地语料里「预期会响亮失败」的目录名（与语料检查同一约定）。 */
    private static final String REJECT_DIR = "07_reject";

    /** 单个文件的结果分类。 */
    private enum Kind {
        PASS("通过"),
        SKIP("已声明限制"),
        EXPECTED_FAIL("按预期响亮失败"),
        UNSUPPORTED_SYNTAX("语法未支持"),
        CONVERTER_BUG("转换器崩溃"),
        BAD_OUTPUT("输出不合法"),
        EXPECTATION_VIOLATION("与声明的预期不符");

        final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    /** 一个文件的检查结果。 */
    private record Outcome(Kind kind, String reason, boolean netRegression) {
    }

    private static final List<String> FAILURES = new ArrayList<>();
    private static final Map<String, Integer> SYMPTOMS = new LinkedHashMap<>();
    private static final Map<String, List<String>> SYMPTOM_SAMPLES = new LinkedHashMap<>();
    private static final Map<String, Integer> SKIP_REASONS = new LinkedHashMap<>();
    private static final Map<String, Integer> UNSUPPORTED_REASONS = new LinkedHashMap<>();

    /**
     * 明细报告**边扫边写**，不在内存里攒列表。
     *
     * <p>外部语料动辄几万个文件，攒成一个 List 再最后落盘既占内存又容易出岔子；
     * 流式写不会。</p>
     */
    private static BufferedWriter reportOut;
    private static Path reportPath;
    private static IOException reportError;

    private static int passCount;
    private static int loudFailCount;
    private static int converterBugCount;
    private static int badOutputCount;
    private static int expectationCount;
    private static int netRegressionCount;
    private static int knownCount;
    private static int idempotentCount;
    private static int notIdempotentCount;

    public static void main(String[] args) throws IOException {
        Path root = ModernJSScriptCorpusCheck.corpusRoot();
        List<Path> scripts = collect(root);
        if (scripts.isEmpty()) {
            throw new AssertionError("没在 " + root + " 下找到任何 .js 脚本");
        }
        Set<String> baseline = readBaseline();
        openReport(root);

        System.out.println("ModernJS 转换结果合法性扫描：" + root);
        System.out.println("共 " + scripts.size() + " 个脚本");
        System.out.println("判据：转换结果必须能被 Rhino 的 parser 解析（线上校验的那一步）");
        if (!baseline.isEmpty()) {
            System.out.println("baseline：" + baseline.size() + " 条已知失败（不判失败）");
        }
        System.out.println();

        Map<String, int[]> perCategory = new TreeMap<>();
        String currentCategory = null;
        for (Path script : scripts) {
            String relative = root.relativize(script).toString().replace('\\', '/');
            String category = relative.contains("/")
                    ? relative.substring(0, relative.indexOf('/')) : ".";
            if (!category.equals(currentCategory)) {
                flushCategory(perCategory, currentCategory);
                currentCategory = category;
            }
            int[] stat = perCategory.computeIfAbsent(category, k -> new int[4]);
            checkOne(script, relative, stat, baseline);
        }
        flushCategory(perCategory, currentCategory);

        printSummary(root, baseline);
        writeReport();

        if (!FAILURES.isEmpty()) {
            throw new AssertionError("ModernJS 转换结果合法性扫描未通过："
                    + FAILURES.size() + " 个脚本的转换结果有问题");
        }
    }

    // ── 单个文件 ──────────────────────────────────────────────────────

    private static void checkOne(Path script, String relative, int[] stat, Set<String> baseline) {
        stat[0]++;
        Outcome outcome;
        try {
            String source = Files.readString(script, StandardCharsets.UTF_8);
            // 只认 07_reject 目录，不认单独的 expect-error 注释。
            // 原因：语料里 expect-error 的含义是「整条链路（含运行期）必须失败」，
            // 例如 08_quirks/01 期望的是 ReferenceError —— 而扫描器**故意不执行**脚本，
            // 那种文件在这里「通过」是正常的，不该判成与预期不符。
            // 07_reject 表达的是「parse 层就该被拒绝」，那才是扫描器能验、也该验的契约。
            boolean expectsFail = relative.startsWith(REJECT_DIR + "/");
            Matcher marker = ModernJSScriptCorpusCheck.EXPECT_ERROR.matcher(source);
            String fragment = expectsFail && marker.find() ? marker.group(1).trim() : "";
            outcome = inspect(source, relative, expectsFail, fragment);
        } catch (IOException e) {
            outcome = new Outcome(Kind.BAD_OUTPUT, "读文件失败：" + e, false);
        }
        if (VERBOSE) {
            System.out.printf("  %-8s %s%n", outcome.kind().name(), relative);
        }
        report(relative, outcome, stat, baseline);
    }

    /**
     * 对一份源码跑完整判据（含语料自带的 {@code expect-error} 约定）。
     *
     * @param expectsFail   该文件声明了「必须响亮失败」（{@code // expect-error} 或放在 {@code 07_reject} 下）
     * @param expectFragment {@code expect-error: 片段} 里的片段，报错必须含它；没有则空串
     */
    static Outcome inspect(String source, String name, boolean expectsFail, String expectFragment) {
        Outcome base = inspectBase(source, name);
        if (!expectsFail) {
            return base;
        }
        if (base.kind() == Kind.CONVERTER_BUG) {
            // 转换器崩了就是 bug，不能拿「反正这个文件预期会失败」盖过去
            return base;
        }
        if (base.kind() == Kind.PASS) {
            return new Outcome(Kind.EXPECTATION_VIOLATION,
                    "声明了必须响亮失败（expect-error / 07_reject），却一路通过了"
                            + " —— 这正是「静默接受一个处理不了的写法」的风险", false);
        }
        if (!expectFragment.isEmpty() && !base.reason().contains(expectFragment)) {
            return new Outcome(Kind.EXPECTATION_VIOLATION,
                    "报错里应含「" + expectFragment + "」，实际是：" + base.reason(), false);
        }
        return new Outcome(Kind.EXPECTED_FAIL, base.reason(), false);
    }

    private static Outcome inspectBase(String source, String name) {
        String transformed;
        try {
            // rhinoTarget = true：与线上 Rhino 目标一致（会先过「静默算错」安全网）
            transformed = ModernJSParser.parse(source, true);
        } catch (ModernJSParseException e) {
            if (e.isConverterBug()) {
                // 崩溃要报出真正的异常类型（NPE / 越界…），外层的 ModernJSParseException
                // 只是包装，说"ModernJSParseException"对定位没有帮助
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                return new Outcome(Kind.CONVERTER_BUG,
                        "转换器自身崩溃：" + shortMessage(describe(cause)), false);
            }
            return new Outcome(Kind.SKIP, shortMessage(e.describe()), false);
        } catch (Throwable t) {
            // parse 不该漏出别的异常；漏出来本身就是 bug
            return new Outcome(Kind.CONVERTER_BUG, "抛出了非预期异常：" + describe(t), false);
        }

        Context cx = CONTEXT_FACTORY.enter();
        try {
            new dev.latvian.mods.rhino.Parser(cx).parse(transformed, name, 0);
        } catch (Throwable t) {
            boolean rawParses = parsesUnderRhino(cx, source, name + "(原文)");
            // 关键区分：转换器**动过**这份源码吗？
            //   - 没动过、原文 Rhino 本来也解析不了 → 这是「这类语法还不支持」，
            //     线上的处理是校验拦下 + 提示，属于响亮失败，不是转换器的错。
            //   - 动过、结果却不合法 → 转换器改坏了，这才是要抓的。
            // 外部语料里绝大多数是前者（模块、生成器、私有字段…），不分开的话
            // 几万条失败混在一起，真正的问题会被淹掉。
            if (!transformed.equals(source) || rawParses) {
                return new Outcome(Kind.BAD_OUTPUT,
                        badOutputReason(source, transformed, t, rawParses), rawParses);
            }
            return new Outcome(Kind.UNSUPPORTED_SYNTAX,
                    unsupportedReason(transformed, t), false);
        }

        // 幂等性：转换结果再转换一次应当不再变化。只统计上报，不判失败
        // （它是「转换器稳定性」的线索，不是线上会踩到的问题）。
        try {
            String again = ModernJSParser.parse(transformed, true);
            if (again.equals(transformed)) {
                idempotentCount++;
            } else {
                notIdempotentCount++;
                reportLine("[非幂等] " + name + "：转换结果再转换一次会变");
            }
        } catch (Throwable t) {
            notIdempotentCount++;
            reportLine("[非幂等] " + name + "：转换结果再转换时会抛 " + describe(t));
        }
        return new Outcome(Kind.PASS, "", false);
    }

    /**
     * 「语法未支持」的原因：粗粒度标出这份源码里最可能解析不了的现代语法 + Rhino 的报错。
     *
     * <p>外部语料上这一桶是最大的一桶（test262 里模块、生成器、私有字段…全在里面）。
     * 只留 Rhino 的报错的话全是 `syntax error`，看不出到底缺什么；加上特征标注之后
     * 汇总出来就是一张「还缺哪些语法」的覆盖率表。</p>
     */
    private static String unsupportedReason(String source, Throwable t) {
        return featureLabel(source) + "：" + describe(t);
    }

    /**
     * 粗粒度判断源码用到了哪类「Rhino 解析不了」的语法。
     *
     * <p>只在掩码文本上找，字符串与注释里的这些记号不算（模板串整段被掩掉，
     * 所以模板串不会被识别出来——它本来就多数能用，无所谓）。判断刻意粗糙：
     * 这里要的是统计口径稳定，不是精确识别。</p>
     */
    private static String featureLabel(String source) {
        String m = ModernJSMask.mask(source);
        if (m.contains("import ") || m.contains("export ")) {
            return "模块（import/export）";
        }
        if (m.contains("function*")) {
            return "生成器（function*）";
        }
        if (m.contains("async")) {
            return "async";
        }
        if (m.contains("yield")) {
            return "yield";
        }
        if (m.contains("?.")) {
            return "可选链 / 可选调用";
        }
        if (m.contains("=>")) {
            return "箭头函数";
        }
        if (m.contains("...")) {
            return "展开 / 剩余";
        }
        if (m.contains(" of ")) {
            return "for-of";
        }
        if (m.contains("class")) {
            return "class";
        }
        if (m.contains("let ") || m.contains("const ")) {
            return "let / const";
        }
        return "未归类";
    }

    /** 拼出「输出不合法」的完整诊断，尽量对齐线上给用户看的信息。 */    private static String badOutputReason(String source, String transformed, Throwable t,
                                          boolean rawParses) {
        StringBuilder sb = new StringBuilder();
        int generatedLine = rhinoLine(t);
        int originalLine = generatedLine > 0
                ? OriginalLineMapper.toOriginalLine(transformed, source, generatedLine) : 0;
        // 带上语法特征标注：外部语料上「转换器动过但结果不合法」的几千条里，绝大多数是
        // 「类转了、但文件里还有生成器/async/私有字段」这种部分支持，不是转换器的错。
        // 标出特征之后就能把「只用了受支持语法却被改坏」的那几条挑出来（特征＝未归类）。
        sb.append("[").append(featureLabel(source)).append("] 输出过不了 Rhino parser：")
                .append(describe(t));
        if (generatedLine > 0) {
            sb.append("（转换后第 ").append(generatedLine).append(" 行");
            if (originalLine > 0) {
                sb.append(" → 原文第 ").append(originalLine).append(" 行");
            }
            sb.append("）");
        }
        if (originalLine > 0) {
            String line = OriginalLineMapper.lineText(source, originalLine);
            if (!line.isEmpty()) {
                sb.append("  |  ").append(line);
            }
        }
        // 与线上一致的两条「残留」诊断
        if (hasCodeWord(transformed, "class")) {
            sb.append("  ->  结果里仍然有 `class`，说明它没有被转换（嵌套在方法体里、"
                    + "或与其它代码同行的 class 暂不支持）");
        }
        if (ModernJSSugarConverter.hasLeftoverPatternDefault(transformed)) {
            sb.append("  ->  结果里仍然有「带默认值的解构模式」，说明它没有被转换");
        }
        String hint = ModernJSSyntaxGuard.explainUnsupported(source);
        if (hint != null) {
            sb.append("  ->  ").append(hint);
        }
        if (rawParses) {
            sb.append("  ->  【净回归】原文本来能被 Rhino 解析，转换之后反而不合法");
        }
        return sb.toString();
    }

    // ── 结果记录 ──────────────────────────────────────────────────────

    /**
     * 统计并上报一个结果。
     *
     * <p>分类数组的含义：{@code [0]} 总数、{@code [1]} 通过、{@code [2]} 已声明限制、
     * {@code [3]} 失败（<b>不含</b> baseline 里已登记的那些）。</p>
     */
    private static void report(String relative, Outcome outcome, int[] stat, Set<String> baseline) {
        boolean failure = outcome.kind() == Kind.CONVERTER_BUG
                || outcome.kind() == Kind.BAD_OUTPUT
                || outcome.kind() == Kind.EXPECTATION_VIOLATION;
        boolean known = failure && baseline.contains(relative);

        reportLine(outcome.kind().label + (known ? "（已知）" : "") + "：" + relative
                + (outcome.reason().isEmpty() ? "" : " -> " + outcome.reason()));

        switch (outcome.kind()) {
            case PASS -> {
                passCount++;
                stat[1]++;
            }
            case SKIP -> {
                // 转换阶段就拒绝
                loudFailCount++;
                stat[2]++;
                SKIP_REASONS.merge(signature(outcome.reason()), 1, Integer::sum);
            }
            case EXPECTED_FAIL -> {
                // 语料自己声明了预期失败，且确实失败了
                loudFailCount++;
                stat[2]++;
            }
            case UNSUPPORTED_SYNTAX -> {
                // 转换器没动它、原文 Rhino 也解析不了：属于「这类语法还不支持」，
                // 线上由校验拦下并提示，是响亮失败
                loudFailCount++;
                stat[2]++;
                UNSUPPORTED_REASONS.merge(signature(outcome.reason()), 1, Integer::sum);
            }
            case CONVERTER_BUG -> converterBugCount++;
            case BAD_OUTPUT -> badOutputCount++;
            case EXPECTATION_VIOLATION -> expectationCount++;
        }

        if (!failure) {
            return;
        }
        if (known) {
            knownCount++;
            System.out.printf("[已知] %-46s %s%n", relative, shortMessage(outcome.reason()));
            return;
        }
        if (outcome.netRegression()) {
            netRegressionCount++;
        }
        stat[3]++;
        String symptom = signature(outcome.reason());
        SYMPTOMS.merge(symptom, 1, Integer::sum);
        SYMPTOM_SAMPLES.computeIfAbsent(symptom, k -> new ArrayList<>())
                .add(relative + " -> " + shortMessage(outcome.reason()));
        FAILURES.add(relative + " -> " + outcome.reason());
    }

    // ── 汇总 ──────────────────────────────────────────────────────────

    private static void flushCategory(Map<String, int[]> perCategory, String category) {
        if (category == null) {
            return;
        }
        int[] stat = perCategory.get(category);
        System.out.printf("  %-18s 脚本 %3d 个：通过 %3d，响亮失败 %3d，失败 %3d%n",
                category, stat[0], stat[1], stat[2], stat[3]);
    }

    private static void printSummary(Path root, Set<String> baseline) {
        System.out.println();
        System.out.println("汇总：");
        System.out.printf("  通过（转换结果合法）  %d%n", passCount);
        System.out.printf("  响亮失败（拒绝 / 语法未支持）%d%n", loudFailCount);
        System.out.printf("  转换器崩溃（含 cause）%d%n", converterBugCount);
        System.out.printf("  输出不合法            %d，其中净回归 %d%n", badOutputCount, netRegressionCount);
        System.out.printf("  与声明预期不符        %d%n", expectationCount);
        if (knownCount > 0) {
            System.out.printf("  以上失败中已登记基线  %d 个（不判失败）%n", knownCount);
        }
        System.out.printf("  幂等：稳定 %d，会变 %d%n", idempotentCount, notIdempotentCount);

        if (!SKIP_REASONS.isEmpty()) {
            System.out.println();
            System.out.println("转换阶段就拒绝的原因分布"
                    + "（响亮失败里属于「明确拒绝」的那部分；不等于「这个写法被支持」）：");
            SKIP_REASONS.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(10)
                    .forEach(e -> System.out.printf("  %3d × %s%n", e.getValue(), e.getKey()));
        }

        if (!UNSUPPORTED_REASONS.isEmpty()) {
            System.out.println();
            System.out.println("语法未支持的原因分布（按「缺哪类语法」聚合，前 15）：");
            UNSUPPORTED_REASONS.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(15)
                    .forEach(e -> System.out.printf("  %5d × %s%n", e.getValue(), e.getKey()));
        }

        if (!SYMPTOMS.isEmpty()) {
            System.out.println();
            System.out.println("失败按症状聚合（每类最多打印 " + SHOW_PER_SYMPTOM + " 条）：");
            SYMPTOMS.forEach((symptom, count) -> {
                System.out.printf("  [%d 例] %s%n", count, symptom);
                SYMPTOM_SAMPLES.get(symptom).stream().limit(SHOW_PER_SYMPTOM)
                        .forEach(sample -> System.out.println("        - " + sample));
            });
        }

        if (!baseline.isEmpty()) {
            Set<String> seen = new LinkedHashSet<>();
            for (String failure : FAILURES) {
                int arrow = failure.indexOf(" -> ");
                if (arrow > 0) {
                    seen.add(failure.substring(0, arrow));
                }
            }
            List<String> fixed = baseline.stream().filter(b -> !seen.contains(b)).sorted().toList();
            if (!fixed.isEmpty()) {
                System.out.println();
                System.out.println("baseline 里这些条目现在不再失败，可以从基线移除：");
                fixed.forEach(b -> System.out.println("  - " + b));
            }
        }

        System.out.println();
        if (FAILURES.isEmpty()) {
            System.out.println("结果：全部通过（" + passCount + " 个通过，"
                    + loudFailCount + " 个响亮失败）");
        } else {
            System.out.println("结果：失败 " + FAILURES.size() + " 个（详情见报告文件）");
        }
    }

    private static void writeReport() throws IOException {
        closeReport();
    }
    /** 打开明细报告：文件名带上语料目录名，免得不同语料的结果互相覆盖。 */
    private static void openReport(Path root) throws IOException {
        String name = root.getFileName() == null ? "corpus" : root.getFileName().toString();
        reportPath = Paths.get("build", "modernjs-sweep-" + sanitize(name) + ".txt")
                .toAbsolutePath();
        Files.createDirectories(reportPath.getParent());
        reportOut = Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8);
    }

    /** 明细写一行（流式）。写失败先记下来，最后一并报，不打断扫描。 */
    private static void reportLine(String line) {
        if (reportOut == null) {
            return;
        }
        try {
            // 换行折成空格：一条明细必须占一行，否则明细条数会和统计对不上
            reportOut.write(line.replace('\n', ' '));
            reportOut.newLine();
        } catch (IOException e) {
            reportError = e;
        }
    }

    private static void closeReport() throws IOException {
        if (reportOut != null) {
            reportOut.close();
            reportOut = null;
        }
        if (reportError != null) {
            throw reportError;
        }
        System.out.println("完整明细：" + reportPath);
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    // ── 输入 ──────────────────────────────────────────────────────────

    private static Set<String> readBaseline() throws IOException {
        String configured = System.getProperty(BASELINE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return Set.of();
        }
        Path path = Paths.get(configured).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new AssertionError("baseline 文件不存在：" + path);
        }
        Set<String> entries = new LinkedHashSet<>();
        for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            entries.add(line.replace('\\', '/'));
        }
        return entries;
    }

    private static List<Path> collect(Path root) throws IOException {
        List<Path> scripts = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".js"))
                    .forEach(scripts::add);
        }
        scripts.sort(Comparator.comparing(Path::toString));
        return scripts;
    }

    // ── 小工具 ────────────────────────────────────────────────────────

    /** 原文能不能过 Rhino parser（用来判定「净回归」）。 */
    private static boolean parsesUnderRhino(Context cx, String source, String name) {
        try {
            new dev.latvian.mods.rhino.Parser(cx).parse(source, name, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 结果里是否还残留某个关键字（只看代码部分，字符串 / 注释里的不算）。 */
    private static boolean hasCodeWord(String text, String word) {
        String masked = ModernJSMask.mask(text);
        int from = 0;
        while (true) {
            int at = masked.indexOf(word, from);
            if (at < 0) {
                return false;
            }
            from = at + 1;
            boolean allCode = true;
            for (int i = at; i < at + word.length(); i++) {
                if (masked.charAt(i) == ModernJSMask.MASK) {
                    allCode = false;
                    break;
                }
            }
            if (!allCode) {
                continue;
            }
            boolean beforeOk = at == 0 || !isIdentPart(masked.charAt(at - 1));
            int after = at + word.length();
            boolean afterOk = after >= masked.length() || !isIdentPart(masked.charAt(after));
            if (beforeOk && afterOk) {
                return true;
            }
        }
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static int rhinoLine(Throwable t) {
        if (t instanceof dev.latvian.mods.rhino.RhinoException e && e.lineNumber() > 0) {
            return e.lineNumber();
        }
        return 0;
    }

    /** 症状签名：抹掉文件名 / 行号 / 数字，好让同类失败聚到一起。 */
    private static String signature(String reason) {
        String text = reason
                // Rhino 的报错里带着 "(路径#行号)"，不抹掉的话每条签名都唯一，聚合等于没做
                .replaceAll("\\([^()]*#[0-9]+\\)", "")
                .replaceAll("\\d+", "#")
                .trim();
        return text.length() <= 90 ? text : text.substring(0, 90) + "...";
    }

    private static String shortMessage(String text) {
        String one = text.replace('\n', ' ').trim();
        return one.length() <= 120 ? one : one.substring(0, 120) + "...";
    }

    private static String describe(Throwable t) {
        String message = String.valueOf(t.getMessage()).replace('\n', ' ').trim();
        return t.getClass().getSimpleName() + (message.isEmpty() ? "" : ": " + message);
    }

    private static int intProperty(String key, int fallback) {
        try {
            return Integer.parseInt(System.getProperty(key, String.valueOf(fallback)).trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
