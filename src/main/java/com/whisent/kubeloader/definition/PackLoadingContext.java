package com.whisent.kubeloader.definition;

import com.whisent.kubeloader.mixin.AccessScriptManager;
import dev.latvian.mods.kubejs.script.*;

/**
 * @author ZZZank
 */
public class PackLoadingContext {
    private final ScriptManager manager;

    public PackLoadingContext(ScriptManager manager) {
        this.manager = manager;
    }

    public ScriptType type() {
        return manager.scriptType;
    }

    public ScriptManager manager() {
        return manager;
    }

    public void loadFile(ScriptPack pack, ScriptFileInfo fileInfo, ScriptSource source) {
        var access = (AccessScriptManager) manager;
        access.kubeLoader$loadFile(pack, fileInfo, source);
    }
}
