package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.scripts.KLScriptLoader;
import com.whisent.kubeloader.klm.MixinManager;
import com.whisent.kubeloader.klm.dsl.MixinDSL;
import com.whisent.kubeloader.utils.Debugger;
import dev.latvian.mods.kubejs.script.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(value = ScriptFile.class, remap = false)
public class ScriptFileMixin {
    @Shadow @Final public ScriptFileInfo info;


    @Shadow @Final public ScriptPack pack;

    @Shadow @Final public ScriptSource source;

    @Inject(method = "load", at = @At("HEAD"), cancellable = true)
    public void kubeLoader$load(CallbackInfo ci) throws Throwable {
        // 检查脚本是否包含mixin属性
        Debugger.out("加载脚本 " + this.info.location);
        Debugger.out("mixin DSL Map: " + kubeLoader$getMixinDSL().keySet());
        KLScriptLoader.load(this.pack, this.info, kubeLoader$getMixinDSL(), ci);


    }

    private Map<String,List<MixinDSL>> kubeLoader$getMixinDSL() {
        return MixinManager.getMixinMap();
    }


}