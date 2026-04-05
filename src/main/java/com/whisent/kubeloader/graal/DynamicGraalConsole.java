package com.whisent.kubeloader.graal;

import dev.latvian.mods.kubejs.util.ConsoleJS;
//import com.whisent.kubeloader.graal.wrapper.WrapperHelper;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.HashMap;
import java.util.Map;

public class DynamicGraalConsole implements ProxyObject {
    private final ConsoleJS console;
    private final String sourceLocation;
    private final Map<String, ProxyExecutable> members = new HashMap<>();

    public DynamicGraalConsole(ConsoleJS console, String sourceLocation) {
        this.console = console;
        this.sourceLocation = sourceLocation;
        members.put("__log", createLogMethod("log"));
        members.put("__info", createLogMethod("info"));
        members.put("__debug", createLogMethod("debug"));
        members.put("__warn", createLogMethod("warn"));
        members.put("__error", createLogMethod("error"));
    }
    
    private ProxyExecutable createLogMethod(String level) {
        return args -> {
            if (args.length >= 3) {
                String filePath = args[0].asString();
                String line = args[1].asString();
                Value messages = args[2];
                String formatted = formatMessages(messages);
                String fullMsg = filePath + "#" + line + ": " + formatted;
                switch(level) {
                    case "log": console.log(fullMsg); break;
                    case "info": console.info(fullMsg); break;
                    case "debug": console.debug(fullMsg); break;
                    case "warn": console.warn(fullMsg); break;
                    case "error": console.error(fullMsg); break;
                }
            }
            return null;
        };
    }
    
    private String formatMessages(Value messages) {
        StringBuilder sb = new StringBuilder();
        long size = messages.getArraySize();
        for (long i = 0; i < size; i++) {
            if (i > 0) sb.append(" ");
            Value v = messages.getArrayElement(i);
            if (v.isNull()) sb.append("null");
            else if (v.isString()) sb.append(v.asString());
            else if (v.isNumber() || v.isBoolean()) sb.append(v.toString());
            else {
                sb.append(v);
            }
        }
        return sb.toString();
    }

    @Override
    public Object getMember(String name) {
        return members.get(name);
    }

    @Override
    public Object getMemberKeys() {
        return members.keySet().toArray();
    }

    @Override
    public boolean hasMember(String name) {
        return members.containsKey(name);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("Cannot modify console");
    }
}
