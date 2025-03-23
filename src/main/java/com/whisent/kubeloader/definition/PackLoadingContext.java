package com.whisent.kubeloader.definition;

import com.whisent.kubeloader.mixin.AccessScriptManager;
import dev.latvian.mods.kubejs.script.*;
import dev.latvian.mods.kubejs.util.ConsoleJS;

/**
 * @author ZZZank
 */
public class PackLoadingContext {
    private final ScriptManager manager;
    private final String folderName;

    public PackLoadingContext(ScriptManager manager) {
        this.manager = manager;
        this.folderName = folderName(type());
    }

    public ScriptType type() {
        return manager.scriptType;
    }

    public String folderName() {
        return folderName;
    }

    public static String folderName(ScriptType type) {
        return type.name + "_scripts";
    }

    public ScriptManager manager() {
        return manager;
    }

    public ConsoleJS console() {
        return type().console;
    }

    public void loadFile(ScriptPack pack, ScriptFileInfo fileInfo, ScriptSource source) {
        var access = (AccessScriptManager) manager;
        access.kubeLoader$loadFile(pack, fileInfo, source);
    }
}
