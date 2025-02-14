package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.Kubeloader;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(ScriptManager.class)
public class ScriptManagerMixin {
    @Shadow @Final public ScriptType scriptType;
    @Unique
    private Boolean kubeloader$sign = false;
    @Inject(method = "loadFromDirectory",at = @At("HEAD"),remap = false)
    private void loadScripts(CallbackInfo ci) {
        if (!kubeloader$sign) {
            if (Objects.equals(this.scriptType.name, "startup")){
                Kubeloader.loadScripts("startup");
                kubeloader$sign = true;
            }

        }
    }
}
