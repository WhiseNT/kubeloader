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
            graalPack.kubeLoader$setGraalContext(manager.getKubeLoader$scriptContexts().get(namespace));
            // Note: DynamicGraalConsole is now created per-script-file in KLScriptLoader
            graalPack.kubeLoader$setDynamicGraalConsole(new DynamicGraalConsole(scriptType.console, ""));
        }
    }
    public static void loadScriptPack(ScriptManagerInterface manager,String namespace) {
        if (manager.getKubeLoader$scriptContexts().get(namespace) == null) {
            manager.getKubeLoader$scriptContexts().put(namespace,
                    GraalApi.createContext((ScriptManager) manager));
        } else {
            ((Context) manager.getKubeLoader$scriptContexts().get(namespace)).close();
            manager.getKubeLoader$scriptContexts().put(namespace,
                    GraalApi.createContext((ScriptManager) manager));
        }
    }
    public static void setContext(ScriptManagerInterface manager, ScriptType scriptType,List<ScriptPack> scriptPacks) {
        scriptPacks.forEach(p -> {
            var graalPack = ((GraalPack)p);
            graalPack.kubeLoader$setGraalContext((Context) manager.getKubeLoader$scriptContexts().get(scriptType.name));
            // Note: DynamicGraalConsole is now created per-script-file in KLScriptLoader
            graalPack.kubeLoader$setDynamicGraalConsole(new DynamicGraalConsole(scriptType.console, ""));
        });
    }
}
