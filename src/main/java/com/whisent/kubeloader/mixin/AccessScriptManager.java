package com.whisent.kubeloader.mixin;

import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * @author ZZZank
 */
@Mixin(value = ScriptManager.class, remap = false)
public interface AccessScriptManager {

    @Invoker("loadFile")
    void kubeLoader$loadFile(ScriptPack pack, ScriptFileInfo fileInfo, ScriptSource source);
}
