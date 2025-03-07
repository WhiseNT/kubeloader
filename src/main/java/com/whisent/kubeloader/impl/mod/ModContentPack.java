package com.whisent.kubeloader.impl.mod;

import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraftforge.forgespi.language.IModInfo;

import java.util.EnumMap;
import java.util.Map;

/**
 * @author ZZZank
 */
public class ModContentPack implements ContentPack {
    private final IModInfo mod;
    final Map<ScriptType, ScriptPack> packs = new EnumMap<>(ScriptType.class);

    public ModContentPack(IModInfo mod) {
        this.mod = mod;
    }

    @Override
    public String getNamespace() {
        return mod.getModId();
    }

    @Override
    public ScriptPack getPack(PackLoadingContext context) {
        return packs.get(context.type());
    }
}
