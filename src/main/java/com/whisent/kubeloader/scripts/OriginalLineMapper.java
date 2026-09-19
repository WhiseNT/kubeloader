package com.whisent.kubeloader.scripts;

/**
 * 把「转换后代码」的行号换算回「原始脚本」的行号。
 *
 * <p>为什么需要：Rhino 报错给的行号基于 <b>转换后</b> 的代码，而类会被展开成
 * {@code function + prototype}，行数和内容都变了，原始脚本里根本不存在那一行，
 * 用户照着去找会被引向错误结论（issue #22 的报告者就因此把原因猜成了「默认参数」）。</p>
 *
 * <p>算法：把转换后与原始源码按行做双指针对齐——生成行若能在原始源码里找到
 * （从上次匹配位置往后找），就取该行；找不到（说明是转换生成的新行，例如类展开、
 * 默认值下沉插入的语句）就归到当前已对齐到的原始位置。</p>
 */
public final class OriginalLineMapper {

    private OriginalLineMapper() {
    }

    /**
     * @return 数组下标 i 对应「转换后第 i+1 行」在原始源码里的行号（1 起）
     */
    public static int[] buildLineMap(String generated, String original) {
        String[] g = splitLines(generated);
        String[] o = splitLines(original);
        int[] map = new int[g.length];

        int oi = 0;
        for (int gi = 0; gi < g.length; gi++) {
            String want = g[gi].trim();
            int found = -1;
            if (!want.isEmpty()) {
                for (int j = oi; j < o.length; j++) {
                    if (o[j].trim().equals(want)) {
                        found = j;
                        break;
                    }
                }
            }
            if (found >= 0) {
                map[gi] = found + 1;
                oi = found + 1;
            } else {
                // 转换生成的新行（类展开、默认值下沉等）：归属到「下一个待匹配的原始行」，
                // 也就是被替换掉的那一段的开头——这样类体里的错误会指向 class 那一行，
                // 而不是指向类之前的语句。
                map[gi] = Math.min(oi + 1, Math.max(o.length, 1));
            }
        }
        return map;
    }

    /** 单点查询：转换后第 generatedLine 行对应原始源码的哪一行（查不到返回 0）。 */
    public static int toOriginalLine(String generated, String original, int generatedLine) {
        if (generatedLine <= 0) return 0;
        int[] map = buildLineMap(generated, original);
        if (generatedLine > map.length) return 0;
        return map[generatedLine - 1];
    }

    /** 取原始源码第 line 行原文（1 起，已 trim）；越界返回空串。 */
    public static String lineText(String source, int line) {
        if (source == null || line <= 0) return "";
        String[] lines = splitLines(source);
        if (line > lines.length) return "";
        return lines[line - 1].trim();
    }

    private static String[] splitLines(String s) {
        if (s == null || s.isEmpty()) return new String[0];
        return s.split("\\r?\\n", -1);
    }
}
