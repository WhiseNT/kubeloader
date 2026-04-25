package com.whisent.kubeloader.graal;

import com.whisent.kubeloader.impl.mixin.GraalPack;
import com.whisent.kubeloader.impl.mixin.ScriptManagerInterface;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptType;
import org.graalvm.polyglot.Context;

import java.util.List;

public class GraalScriptManager {
    public static void loadContentPack(ScriptManagerInterface manager, ScriptType scriptType, ScriptPack scriptPack, String namespace) {
        loadScriptPack(manager, namespace);
        if (scriptPack != null) {
            var graalPack = ((GraalPack)scriptPack);
            Object context = manager.getKubeLoader$scriptContexts().get(namespace);
            if (context == null) {
                System.err.println("[KubeLoader] ERROR: No GraalJS context found for namespace: " + namespace);
                return;
            }
            graalPack.kubeLoader$setGraalContext(context);
            System.out.println("[KubeLoader] Set GraalJS context for pack: " + scriptPack.info.namespace + " with namespace: " + namespace);
            // Note: DynamicGraalConsole is now created per-script-file in KLScriptLoader
            graalPack.kubeLoader$setDynamicGraalConsole(new DynamicGraalConsole(scriptType.console, ""));
        }
    }
    public static void loadScriptPack(ScriptManagerInterface manager,String namespace) {
        System.out.println("[KubeLoader] Loading script pack for namespace: " + namespace);
        if (manager.getKubeLoader$scriptContexts().get(namespace) == null) {
            Context context = GraalApi.createContext((ScriptManager) manager);
            if (context != null) {
                manager.getKubeLoader$scriptContexts().put(namespace, context);
                System.out.println("[KubeLoader] Created new GraalJS context for namespace: " + namespace);
            } else {
                System.err.println("[KubeLoader] Failed to create GraalJS context for namespace: " + namespace);
            }
        } else {
            ((Context) manager.getKubeLoader$scriptContexts().get(namespace)).close();
            Context context = GraalApi.createContext((ScriptManager) manager);
            if (context != null) {
                manager.getKubeLoader$scriptContexts().put(namespace, context);
                System.out.println("[KubeLoader] Replaced existing GraalJS context for namespace: " + namespace);
            } else {
                System.err.println("[KubeLoader] Failed to replace GraalJS context for namespace: " + namespace);
            }
        }
    }
    public static void setContext(ScriptManagerInterface manager, ScriptType scriptType,List<ScriptPack> scriptPacks,String namespace) {
        scriptPacks.forEach(p -> {
            var graalPack = ((GraalPack)p);
            Object context = manager.getKubeLoader$scriptContexts().get(namespace);
            if (context == null) {
                System.err.println("[KubeLoader] ERROR: No GraalJS context found for namespace: " + namespace + " when setting context for pack: " + p.info.namespace);
                return;
            }
            graalPack.kubeLoader$setGraalContext((Context) context);
            System.out.println("[KubeLoader] Set context for pack: " + p.info.namespace + " with namespace: " + namespace);
            // Note: DynamicGraalConsole is now created per-script-file in KLScriptLoader
            graalPack.kubeLoader$setDynamicGraalConsole(new DynamicGraalConsole(scriptType.console, ""));
        });
    }
    public static void setContextForPack(ScriptManagerInterface manager, ScriptPack pack) {
        var graalPack = ((GraalPack) pack);
        String namespace = pack.info.namespace;
        Object context = manager.getKubeLoader$scriptContexts().get(namespace);
        
        if (context == null) {
            // 如果找不到，尝试用 scriptType 名称作为 fallback
            context = manager.getKubeLoader$scriptContexts().get(pack.manager.scriptType.name);
        }
        
        if (context == null) {
            System.err.println("[KubeLoader] ERROR: No GraalJS context found for namespace: " + namespace);
            // 尝试创建一个临时的
            context = GraalApi.createContext((ScriptManager) manager);
            if (context != null) {
                manager.getKubeLoader$scriptContexts().put(namespace, context);
            }
        }
        
        graalPack.kubeLoader$setGraalContext((Context) context);
        graalPack.kubeLoader$setDynamicGraalConsole(new DynamicGraalConsole(pack.manager.scriptType.console, ""));
    }
}
