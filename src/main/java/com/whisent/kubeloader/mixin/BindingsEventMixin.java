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
            System.out.println("[KubeLoader] Binding " + name + " to " + value.getClass().getName());
            var bindings = ((ScriptManagerInterface)thiz().manager).getKubeLoader$bindings();
            
            if (bindings.get(name) == null) {
                bindings.put(name, value);
            } else {
                bindings.remove(name);
                bindings.put(name, value);
            }
        }
    }

    private BindingsEvent thiz() {
        return (BindingsEvent) (Object) this;
    }
}