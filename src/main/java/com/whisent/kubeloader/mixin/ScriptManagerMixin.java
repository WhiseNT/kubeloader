package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.impl.ContentPackProviders;
import dev.latvian.mods.kubejs.script.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;


@Mixin(ScriptManager.class)
public abstract class ScriptManagerMixin {

    @Shadow
    @Final
    public Map<String, ScriptPack> packs;

    @Inject(method = "reload", at = @At(value = "INVOKE", target = "Ldev/latvian/mods/kubejs/script/ScriptManager;load()V"), remap = false)
    private void injectPacks(CallbackInfo ci) {
        var context = new PackLoadingContext(thiz());
        List<ContentPack>[] buckets = new List[21];
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = new ArrayList<>();
        }
        for (var contentPack : ContentPackProviders.getPacks()) {
            Kubeloader.LOGGER.debug("寻找到contentPack: {}", contentPack);

            Object priority = contentPack.getConfig().get("priority");


            var pack = contentPack.getPack(context);
            if (pack != null) {
                this.packs.put(contentPack.getNamespace(context), contentPack.postProcessPack(context, pack));

            }
        }
        Map<String, ScriptPack> newPack = new HashMap<>();
        this.packs.forEach((key, value) -> {

        });
        /*
        for (List<ContentPack> bucket : buckets) {
            for (var contentPack : bucket) {
                var pack = contentPack.getPack(context);
                if (pack != null) {
                    String namespace = contentPack.getNamespace(context);
                    ScriptPack processedPack = contentPack.postProcessPack(context, pack);
                    this.packs.put(namespace, processedPack);
                }
            }
        }
         */

    }

    @Unique
    private ScriptManager thiz() {
        return (ScriptManager) (Object) this;
    }
}
