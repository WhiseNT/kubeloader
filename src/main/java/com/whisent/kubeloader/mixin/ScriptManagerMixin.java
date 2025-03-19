package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.impl.ContentPackProviders;
import dev.latvian.mods.kubejs.script.*;
import it.unimi.dsi.fastutil.Hash;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;


@Mixin(ScriptManager.class)
public abstract class ScriptManagerMixin {

    @Mutable
    @Shadow
    @Final
    public Map<String, ScriptPack> packs;

    @Inject(method = "reload", at = @At(value = "INVOKE", target = "Ldev/latvian/mods/kubejs/script/ScriptManager;load()V"), remap = false)
    private void injectPacks(CallbackInfo ci) {
        var context = new PackLoadingContext(thiz());

        var newPack = new LinkedHashMap<String,ScriptPack>();
        var originPack = new HashMap<String,ScriptPack>();
        List<ContentPack>[] inferBuckets = new List[10];
        List<ContentPack>[] deferBuckets = new List[10];

        this.initBuckets(inferBuckets);
        this.initBuckets(deferBuckets);

        for (var contentPack : ContentPackProviders.getPacks()) {
            Kubeloader.LOGGER.debug("寻找到contentPack: {}", contentPack);
            Object priority = contentPack.getConfig().get("priority");

            int priorityInt = this.getPriorityInt(priority);
            Kubeloader.LOGGER.debug("pack优先级"+priorityInt);
            var pack = contentPack.getPack(context);
            if (pack != null) {
                if (priorityInt != 0) {
                    if (priorityInt >= 0) {
                        inferBuckets[9-priorityInt].add(contentPack);
                    } else if (priorityInt < 0) {
                        priorityInt = 1-priorityInt;
                        deferBuckets[priorityInt].add(contentPack);
                    }
                } else {
                    originPack.putAll(this.packs);
                }
            }
        }
        this.putPack(newPack,inferBuckets,context);
        newPack.putAll(this.packs);
        this.putPack(newPack,deferBuckets,context);
        this.packs = newPack;
    }
    @Unique
    private void initBuckets (List<ContentPack>[] Buckets) {
        for (int i = 0; i < Buckets.length; i++) {
            Buckets[i] = new ArrayList<>();
        }
    }
    @Unique
    private void putPack (LinkedHashMap<String,ScriptPack> newPack, List<ContentPack>[] Bucket , PackLoadingContext context) {
        for (var bucket : Bucket) {
            for (var contentPack : bucket) {
                var pack = contentPack.getPack(context);
                newPack.put(contentPack.getNamespace(context),contentPack.postProcessPack(context, pack));
            }
        }
    }
    @Unique
    private int getPriorityInt (Object priority) {
        int priorityInt = 0;
        if (priority != null) {
            priorityInt = Integer.parseInt(priority.toString());
        }
        return priorityInt;
    }

    @Unique
    private ScriptManager thiz() {
        return (ScriptManager) (Object) this;
    }
}
