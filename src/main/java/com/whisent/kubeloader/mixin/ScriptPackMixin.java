package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.graal.DynamicGraalConsole;
import com.whisent.kubeloader.graal.GraalApi;
import com.whisent.kubeloader.impl.mixin.GraalPack;
import dev.latvian.mods.kubejs.script.ScriptPack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ScriptPack.class,remap = false)
public class ScriptPackMixin implements GraalPack {
    @Unique
    public Object kubeLoader$graalContext;
    @Unique
    private DynamicGraalConsole kubeLoader$dynamicGraalConsole;
    @Unique
    public void kubeLoader$setGraalContext(Object context) {
        GraalApi.setGraalContext(this, context);
        this.kubeLoader$graalContext = context;
    }

    @Override
    public Object kubeLoader$getGraalContext() {
        return kubeLoader$graalContext;
    }
    @Override
    public DynamicGraalConsole kubeLoader$getDynamicGraalConsole() {
        return kubeLoader$dynamicGraalConsole;
    }
    @Unique
    public void kubeLoader$setDynamicGraalConsole(DynamicGraalConsole console) {
        this.kubeLoader$dynamicGraalConsole = console;
    }

    private ScriptPack thiz() {
        return (ScriptPack) (Object) this;
    }
}
