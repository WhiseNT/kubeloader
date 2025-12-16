package com.whisent.kubeloader.graal;

import dev.latvian.mods.kubejs.util.ConsoleJS;

import java.util.Arrays;

public class DynamicGraalConsole {
    private final ConsoleJS console;
    private String sourceLocation = "Unknown";

    public DynamicGraalConsole(ConsoleJS console) {
        this.console = console;
    }
    public void setSourceLocation(String sourceLocation) {
        this.sourceLocation = sourceLocation;
    }
    public String getSourceLocation() {
        return sourceLocation;
    }

    public void log(Object... args) {
        String prefix = buildMessage(); // e.g. "test_content.js: "
        if (args == null || args.length == 1) {
            console.log(prefix+args[0]);
        } else {
            console.log(prefix+ Arrays.stream(args).skip(0));
        }
    }

    public void warn(Object args) {
        console.warn(buildMessage() + args);
    }

    public void error(Object args) {
        console.error(buildMessage() + args);
    }

    private String buildMessage() {
        String messageText;
        try {

            messageText = sourceLocation + ": ";
        } catch (Exception e) {
            messageText = "";
        }
        return messageText;
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