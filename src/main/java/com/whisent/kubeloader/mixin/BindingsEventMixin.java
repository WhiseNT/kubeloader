package com.whisent.kubeloader.mixin;


import com.whisent.kubeloader.compat.JavaWrapperCompat;
import com.whisent.kubeloader.compat.GraalJSCompat;
import com.whisent.kubeloader.definition.inject.SortablePacksHolder;
import com.whisent.kubeloader.impl.mixin.ScriptManagerInterface;
import com.whisent.kubeloader.plugin.ContentPacksBinding;
import dev.latvian.mods.kubejs.script.BindingRegistry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BindingRegistry.class, remap = false)
public class BindingsEventMixin {
    @Inject(method = "add", at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    public void kubeLoader$add(String name, Object value, CallbackInfo ci) {
        if ("Java".equals(name)) {
            thiz().context().addToScope(thiz().scope(), name, JavaWrapperCompat.class);
            return;
        }

        if (GraalJSCompat.canUseGraalJS()) {
            if (name == null || value == null) {
                return;
            }
            var bindings = ((ScriptManagerInterface) thiz().context().kjsFactory.manager).getKubeLoader$bindings();

            if ("ContentPacks".equals(name)) {
                bindings.remove(name);
                var packsHolder = (SortablePacksHolder) thiz().context().kjsFactory.manager;
                bindings.put(name, new ContentPacksBinding(thiz().type(), packsHolder));
                return;
            } else {
                // 移除旧绑定并添加新绑定
                bindings.remove(name);
                bindings.put(name, value);
            }
        }
    }

    private BindingRegistry thiz() {
        return (BindingRegistry) (Object) this;
    }
}
