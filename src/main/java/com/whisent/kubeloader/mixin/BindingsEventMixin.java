package com.whisent.kubeloader.mixin;


import com.whisent.kubeloader.compat.GraalJSCompat;
import com.whisent.kubeloader.impl.mixin.ScriptManagerInterface;
import dev.latvian.mods.kubejs.script.BindingsEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BindingsEvent.class,remap = false)
public class BindingsEventMixin {
    @Inject(method = "add", at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    public void kubeLoader$add(String name, Object value, CallbackInfo ci) {
        if (GraalJSCompat.canUseGraalJS) {
            if (name == null || value == null) {
                return;
            }
            var bindings = ((ScriptManagerInterface)thiz().manager).getKubeLoader$bindings();

            if ("Java".equals(bindings.get(name)) || "Text".equals(bindings.get(name))) {
                // 保留特殊的Java和Text绑定
                return;
            } else {
                // 移除旧绑定并添加新绑定
                bindings.remove(name);
                bindings.put(name, value);
            }
        }
    }

    private BindingsEvent thiz() {
        return (BindingsEvent) (Object) this;
    }
}