package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.graal.context.IdentifiedContext;
import dev.latvian.mods.kubejs.script.ScriptPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ScriptPack.class, remap = false)
public class ScriptPackMixin {

    @Unique
    public IdentifiedContext kubeLoader$identifiedContext;
    public IdentifiedContext kubeLoader$getIdentifiedContext() {
        return kubeLoader$identifiedContext;
    }

}
