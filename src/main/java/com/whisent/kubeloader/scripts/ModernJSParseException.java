package com.whisent.kubeloader.scripts;

/**
 * ModernJS 在转换/扫描阶段发现的“不能安全放过去”的问题。
 *
 * <p>刻意携带 <b>原始</b> 行号与原始行内容（不是转换后的），这样调用方可以直接
 * 输出「文件:行 + 原始代码」。过去把 Rhino 的报错直接抛给用户时，行号和代码行
 * 都是转换后的，用户在源码里根本找不到对应位置，往往被引向错误结论。</p>
 */
public class ModernJSParseException extends RuntimeException {

    /** 原始源码中的行号（从 1 开始） */
    private final int line;
    /** 原始源码中该行的内容（已 trim） */
    private final String sourceLine;
    /** 给用户的改写建议 */
    private final String hint;

    public ModernJSParseException(String message, int line, String sourceLine, String hint) {
        super(message);
        this.line = line;
        this.sourceLine = sourceLine;
        this.hint = hint;
    }

    /**
     * 带 cause 的版本，专用于「转换器自己没预料到的失败」。
     *
     * <p>两种 {@code ModernJSParseException} 对调用方的含义完全不同：</p>
     * <ul>
     *   <li><b>没有 cause</b> —— 踩到了已知限制（{@code ??}、不支持的解构写法…）。
     *       这是预期内的、可以接受的「响亮失败」。</li>
     *   <li><b>有 cause</b> —— 转换器崩了（NPE、下标越界…）。这是 bug，检查里必须单独拎出来。</li>
     * </ul>
     *
     * <p>线上两者的处理一样（都是跳过这个脚本并给一行提示），但检查扫描需要能区分，
     * 否则「转换器崩了」会被当成「已知限制」放过去。</p>
     */
    public ModernJSParseException(String message, int line, String sourceLine, String hint,
                                  Throwable cause) {
        super(message, cause);
        this.line = line;
        this.sourceLine = sourceLine;
        this.hint = hint;
    }

    /** 是不是「转换器自己崩了」（而不是已知限制）。 */
    public boolean isConverterBug() {
        return getCause() != null;
    }

    public int getLine() {
        return line;
    }

    public String getSourceLine() {
        return sourceLine;
    }

    public String getHint() {
        return hint;
    }

    /** 一行式描述，文件名由调用方补在前面 */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("第 ").append(line).append(" 行: ").append(getMessage());
        if (sourceLine != null && !sourceLine.isEmpty()) {
            sb.append("  |  ").append(sourceLine);
        }
        if (hint != null && !hint.isEmpty()) {
            sb.append("  ->  ").append(hint);
        }
        return sb.toString();
    }
}
