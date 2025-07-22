package com.whisent.kubeloader.mixin;

import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(value = ScriptFileInfo.class, remap = false)
public interface AccessScriptFileInfo {
    @Accessor("properties")
    Map<String, List<String>> getProperties();

}
