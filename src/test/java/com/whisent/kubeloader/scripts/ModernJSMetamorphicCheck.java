package com.whisent.kubeloader.scripts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * ModernJS 的「变形测试」（metamorphic testing）。
 *
 * <p>思路：不写新用例，而是把 {@code examples/modernjs} 下**每个脚本机械地改写成更丑的
 * 等价形式**，再整轮跑一遍 {@link ModernJSScriptCorpusCheck}。判据只有一条：</p>
 *
 * <blockquote>只改动空白与注释的变形，不许改变任何脚本的结论。</blockquote>
 *
 * <p>为什么这条判据有用：抓到的 bug 里绝大多数都是「语义没变、只是写法变了，结果却错了」
 * —— 类体里多一行注释就报错、<code>{</code> 单独占一行就丢方法体、{@code static} 后面
 * 用 Tab 就丢成员。这些正是变形测试专门盯的。</p>
 *
 * <p>变形都在**代码位置**上做（用 {@link ModernJSMask#mask} 判断该位置是不是字符串 /
 * 模板串 / 注释内部），保证「只改空白与注释」这个前提成立，不会反过来把字符串内容改掉。</p>
 *
 * <p>运行：{@code ./gradlew modernJSMetamorphicCheck}（已挂在 {@code check} 上）。</p>
 */
public final class ModernJSMetamorphicCheck {

    private static final String CORPUS_PROPERTY = "modernjs.corpus";

    /** 往行尾追加东西的变形：先确认行尾确实落在代码位置。 */
    private static String appendIfCodeTail(String line, int lineStart, char[] masked, String tail) {
        int last = lastNonSpace(line);
        if (last < 0 || masked[lineStart + last] == ModernJSMask.MASK) {
            return line; // 行尾在字符串 / 模板串 / 注释里：不动
        }
        return line + tail;
    }

    /** 在行首做文章的变形：先确认行首第一个非空字符落在代码位置。 */
    private static int codeHead(String line, int lineStart, char[] masked) {
        int first = firstNonSpace(line);
        if (first < 0 || masked[lineStart + first] == ModernJSMask.MASK) {
            return -1;
        }
        return first;
    }

    private static final List<Mutation> MUTATIONS = List.of(
            new Mutation("行尾加注释",
                    (line, start, masked) -> appendIfCodeTail(line, start, masked, " // 变形注释")),
            new Mutation("行前插入注释",
                    (line, start, masked) -> codeHead(line, start, masked) < 0
                            ? line : "/* 变形插入 */ " + line),
            new Mutation("缩进转 Tab",
                    (line, start, masked) -> {
                        int first = codeHead(line, start, masked);
                        if (first <= 0) {
                            return line; // 没缩进，或行首不在代码位置
                        }
                        return "\t".repeat(first) + line.substring(first);
                    }),
            new Mutation("行尾补 CR（CRLF）",
                    (line, start, masked) -> appendIfCodeTail(line, start, masked, "\r")),
            new Mutation("行间插入空行",
                    (line, start, masked) -> appendIfCodeTail(line, start, masked, "\n")));

    public static void main(String[] args) throws IOException {
        Path base = ModernJSScriptCorpusCheck.corpusRoot();
        List<Path> scripts = collect(base);
        System.out.println("ModernJS 变形测试：基于 " + base);
        System.out.println("基准语料 " + scripts.size() + " 个脚本，每种变形都会整轮重跑一遍");
        System.out.println();

        String original = System.getProperty(CORPUS_PROPERTY);
        List<String> failures = new ArrayList<>();
        try {
            for (Mutation mutation : MUTATIONS) {
                Path dir = Files.createTempDirectory("modernjs-meta-");
                try {
                    writeMutated(base, scripts, dir, mutation);
                    if (Boolean.getBoolean("modernjs.meta.keep")) {
                        System.out.println("（保留变形语料）" + dir);
                    } else {
                        dir.toFile().deleteOnExit();
                    }
                    System.setProperty(CORPUS_PROPERTY, dir.toString());
                    ModernJSScriptCorpusCheck.resetState();
                    String thrown = null;
                    try {
                        ModernJSScriptCorpusCheck.main(new String[0]);
                    } catch (Throwable t) {
                        thrown = String.valueOf(t.getMessage());
                    }
                    List<String> variantFailures = ModernJSScriptCorpusCheck.lastFailures();
                    if (variantFailures.isEmpty()) {
                        System.out.printf("[ OK ] %-18s %d 个脚本，结论与基准一致%n",
                                mutation.name(), scripts.size());
                    } else {
                        failures.add(mutation.name() + "：变形后出现 " + variantFailures.size()
                                + " 个失败" + (thrown == null ? "" : "（" + thrown + "）"));
                        variantFailures.forEach(f ->
                                failures.add("    " + mutation.name() + " -> " + f));
                    }
                } finally {
                    if (original == null) {
                        System.clearProperty(CORPUS_PROPERTY);
                    } else {
                        System.setProperty(CORPUS_PROPERTY, original);
                    }
                    if (!Boolean.getBoolean("modernjs.meta.keep")) {
                        deleteRecursively(dir);
                    }
                }
            }
        } finally {
            ModernJSScriptCorpusCheck.resetState();
        }

        System.out.println();
        if (!failures.isEmpty()) {
            System.out.println("结果：变形测试失败 " + failures.size() + " 条");
            failures.forEach(f -> System.out.println("  - " + f));
            throw new AssertionError("变形测试未通过：只改空白/注释就改变了脚本结论，共 "
                    + failures.size() + " 条");
        }
        System.out.println("结果：全部通过（" + MUTATIONS.size() + " 种变形 × "
                + scripts.size() + " 个脚本，结论均与基准一致）");
    }

    /** 一种变形：名字 + 逐行改写（行内容、它在原文里的起始下标、整份源码的掩码）。 */
    private record Mutation(String name, LineMutation lines) {
        String apply(String source) {
            char[] masked = ModernJSMask.mask(source).toCharArray();
            StringBuilder out = new StringBuilder();
            String[] split = source.split("\n", -1);
            int offset = 0;
            for (int i = 0; i < split.length; i++) {
                out.append(lines.apply(split[i], offset, masked));
                if (i < split.length - 1) {
                    out.append('\n');
                }
                offset += split[i].length() + 1;
            }
            return out.toString();
        }
    }

    private interface LineMutation {
        String apply(String line, int lineStart, char[] masked);
    }

    // ── 落盘与遍历 ────────────────────────────────────────────────────

    private static void writeMutated(Path base, List<Path> scripts, Path target, Mutation mutation)
            throws IOException {
        for (Path script : scripts) {
            Path destination = target.resolve(base.relativize(script).toString());
            Files.createDirectories(destination.getParent());
            String source = Files.readString(script, StandardCharsets.UTF_8);
            Files.writeString(destination, mutation.apply(source), StandardCharsets.UTF_8);
        }
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

    private static void deleteRecursively(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 临时目录清不掉不影响结论
                }
            });
        }
    }

    private static int firstNonSpace(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (!Character.isWhitespace(line.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int lastNonSpace(String line) {
        for (int i = line.length() - 1; i >= 0; i--) {
            if (!Character.isWhitespace(line.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
