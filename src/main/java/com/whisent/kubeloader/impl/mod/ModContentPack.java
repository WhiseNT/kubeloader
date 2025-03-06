package com.whisent.kubeloader.impl.mod;

import com.whisent.kubeloader.definition.ContentPack;
import dev.architectury.platform.Mod;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptType;

import java.util.EnumMap;
import java.util.Map;

/**
 * @author ZZZank
 */
public class ModContentPack implements ContentPack {
    private final Mod mod;
    final Map<ScriptType, ScriptPack> packs = new EnumMap<>(ScriptType.class);

    public ModContentPack(Mod mod) {
        this.mod = mod;
    }

    @Override
    public String getNamespace() {
        return mod.getModId();
    }

    @Override
    public ScriptPack getPack(ScriptType type) {
        return packs.get(type);
    }
}
