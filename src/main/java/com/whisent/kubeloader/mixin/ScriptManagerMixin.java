package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.files.topo.ContentPackSorter;
import com.whisent.kubeloader.impl.ContentPackProviders;
import dev.latvian.mods.kubejs.script.*;
import dev.latvian.mods.kubejs.util.ConsoleJS;
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
        var unsortedPacks = new LinkedHashMap<String,ContentPack>();


        for (var contentPack : ContentPackProviders.getPacks()) {
            Kubeloader.LOGGER.debug("寻找到contentPack: {}", contentPack);

            var pack = contentPack.getPack(context);
            if (pack != null) {
                //统计所有的contentpacks
                unsortedPacks.put(contentPack.getNamespace(),contentPack);
            }
        }
        var Sorter = new ContentPackSorter(unsortedPacks);
        Sorter.buildDependencies();
        List<String> sortedOrder = Sorter.getSortedPacks();

        ConsoleJS.STARTUP.log("排序后的pack为"+sortedOrder);
        var originPack = new LinkedHashMap<String,ScriptPack>();
        sortedOrder.forEach(namespace->{
            if (!Objects.equals(namespace, "kubejs")) {
                var contentPack = unsortedPacks.get(namespace);
                Kubeloader.LOGGER.debug(namespace+"获取ContentPack为"+contentPack);
                var pack = contentPack.getPack(context);
                newPack.put(contentPack.getNamespace(context),contentPack.postProcessPack(context, pack));
            } else {
                originPack.putAll(this.packs);
                newPack.putAll(originPack);
            }
        });
        this.packs = newPack;

    }


    @Unique
    private ScriptManager thiz() {
        return (ScriptManager) (Object) this;
    }
}
