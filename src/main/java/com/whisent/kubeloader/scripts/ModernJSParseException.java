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
