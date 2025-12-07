package com.whisent.kubeloader.mixin;


import com.whisent.kubeloader.graal.GraalApi;
import com.whisent.kubeloader.graal.context.ContextMap;
import com.whisent.kubeloader.impl.mixin.ScriptManagerInterface;
import dev.latvian.mods.kubejs.script.BindingsEvent;

import org.graalvm.polyglot.Context;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BindingsEvent.class,remap = false)
public class BindingsEventMixin {
    @Inject(method = "add", at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    public void kubeLoader$add(String name, Object value, CallbackInfo ci) {
        if (value != null) {
            ContextMap.getContexts(((BindingsEvent) (Object) this).manager.scriptType).forEach(identifiedContext -> {
                Context context = identifiedContext.getContext();
                GraalApi.registerBinding(context, name, value);
                //System.out.println("注册绑定: " + name + " -> " + value);
            });

        }
    }
}
