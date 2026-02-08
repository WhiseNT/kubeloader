package com.whisent.kubeloader.graal;

import dev.latvian.mods.kubejs.util.ConsoleJS;

import java.util.Arrays;

public class DynamicGraalConsole {
    private final ConsoleJS console;
    private final String sourceLocation;  // Final - captured at creation time

    public DynamicGraalConsole(ConsoleJS console, String sourceLocation) {
        this.console = console;
        this.sourceLocation = sourceLocation;
    }

    public void log(Object... args) {
        String prefix = buildMessage();
        if (args == null || args.length == 0) {
            console.log(prefix);
        } else if (args.length == 1) {
            console.log(prefix + args[0]);
        } else {
            console.log(prefix + Arrays.stream(args).map(Object::toString).reduce("", (a, b) -> a + " " + b));
        }
    }
    
    public void info(Object... args) {
        console.info(buildMessage() + format(args));
    }

    public void debug(Object... args) {
        console.debug(buildMessage() + format(args));
    }

    public void warn(Object... args) {
        console.warn(buildMessage() + format(args));
    }

    public void error(Object... args) {
        console.error(buildMessage() + format(args));
    }

    private String buildMessage() {
        if (sourceLocation != null && !sourceLocation.isEmpty()) {
            return sourceLocation + ": ";
        }
        return "";
    }
    
    // 获取详细的源码位置信息（包括行号）
    private String getDetailedLocation() {
        try {
            // 在 JS 环境中抛出并捕获错误来获取调用栈
            // 这需要在 JS 侧配合实现
            return buildMessage();
        } catch (Exception e) {
            return buildMessage();
        }
    }

    private String format(Object[] args) {
        if (args == null || args.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }
}