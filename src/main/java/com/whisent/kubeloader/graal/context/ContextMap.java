package com.whisent.kubeloader.graal.context;

import dev.latvian.mods.kubejs.script.ScriptType;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ContextMap {
    private static final Map<ScriptType, Set<IdentifiedContext>> contexts = new ConcurrentHashMap<>();
    private ContextMap() {}
    public static void putContext(IdentifiedContext context) {
        System.out.println("注册Graal上下文: " + context.getId() + " 类型: " + context.getType());
        System.out.println("当前已有上下文数量: " + contexts.values().stream().mapToInt(Set::size).sum());
        if (context != null && context.getType() != null) {
            if (!contexts.containsKey(context.getType())) {
                contexts.put(context.getType(), ConcurrentHashMap.newKeySet());
            }
            if (!contexts.get(context.getType()).contains(context)) {
                contexts.get(context.getType()).add(context);
            }
        }
    }

    public static Set<IdentifiedContext> getContexts(ScriptType type) {
        return contexts.getOrDefault(type, ConcurrentHashMap.newKeySet());
    }
    public static void clearContexts(ScriptType type) {
        Set<IdentifiedContext> set = contexts.get(type);
        if (set != null) {
            set.forEach(context->{
                try {
                    context.close();
                } catch (Exception e) {
                    System.err.println("Failed to close context " + context.getId() + ": " + e.getMessage());
                }
            });
        }
    }
    public static IdentifiedContext getContext(ScriptType type, String id) {
        Set<IdentifiedContext> set = contexts.get(type);
        if (set != null) {
            for (IdentifiedContext context : set) {
                if (context.getId().equals(id)) {
                    return context;
                }
            }
        }
        return null;
    }
    public static void clear() {


    }
    public static void removeContext(IdentifiedContext context) {
        if (context.getType() != null) {
            Set<IdentifiedContext> set = contexts.get(context.getType());
            if (set != null) {
                set.remove(context);
            }
        }
    }
}
