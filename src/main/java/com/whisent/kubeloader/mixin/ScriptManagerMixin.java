package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.Kubeloader;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.script.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Objects;

@Mixin(ScriptManager.class)
public abstract class ScriptManagerMixin {
    @Shadow @Final public ScriptType scriptType;

    @Shadow protected abstract void loadFile(ScriptPack pack, ScriptFileInfo fileInfo, ScriptSource source);

    @Shadow @Final public Map<String, ScriptPack> packs;
    @Unique
    private Boolean kubeloader$sign = false;
    @Inject(method = "load",at = @At("HEAD"),remap = false)
    private void loadScripts(CallbackInfo ci) {

        if (!kubeloader$sign) {

            if (Objects.equals(this.scriptType.name, "startup")){


            }

        }
    }


    @Inject(method = "reload",at = @At("HEAD"),remap = false)
    private void relaoadMixin(CallbackInfo ci) {
        if (kubeloader$sign){
            Kubeloader.getStartupScriptManager().reload(null);
        }

    }
}
