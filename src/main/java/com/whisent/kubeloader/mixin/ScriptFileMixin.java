package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.definition.meta.Engine;
import com.whisent.kubeloader.impl.mixin.ScriptFileInfoInterface;
import com.whisent.kubeloader.scripts.KLScriptLoader;
import com.whisent.kubeloader.klm.MixinManager;
import com.whisent.kubeloader.klm.dsl.MixinDSL;
import com.whisent.kubeloader.utils.Debugger;
import dev.latvian.mods.kubejs.script.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Mixin(value = ScriptFile.class, remap = false)
public class ScriptFileMixin {
    @Unique
    private static final Pattern MIXIN_PATTERN =
            Pattern.compile("^//mixin\\s*\\(\\s*value\\s*=\\s*\"([^\"]+)\"\\s*\\)");

    @Unique
    private static final Pattern SIDE_PATTERN =
            Pattern.compile("^//side\\s*:\\s*([a-zA-Z\\-]+)");

    @Unique
    private static final Pattern ENGINE_PATTERN =
            Pattern.compile("^//engine\\s*:\\s*([a-zA-Z_]+)");

    @Shadow @Final public ScriptFileInfo info;


    @Shadow @Final public ScriptPack pack;

    @Shadow public String[] lines;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void kubeLoader$parseDirectives(ScriptPack pack, ScriptFileInfo info, CallbackInfo ci) {
        var scriptInfo = (ScriptFileInfoInterface) info;

        try {
            for (String rawLine : Files.readAllLines(info.path)) {
                var line = rawLine.trim();
                if (!line.startsWith("//")) {
                    continue;
                }

                var mixinMatcher = MIXIN_PATTERN.matcher(line);
                if (mixinMatcher.find()) {
                    scriptInfo.kubeLoader$setTargetPath(mixinMatcher.group(1));
                }

                var sideMatcher = SIDE_PATTERN.matcher(line);
                if (sideMatcher.find()) {
                    scriptInfo.kubeLoader$getSides().add(sideMatcher.group(1));
                }

                var engineMatcher = ENGINE_PATTERN.matcher(line);
                if (engineMatcher.find()) {
                    try {
                        scriptInfo.kubeLoader$setEngine(Engine.valueOf(engineMatcher.group(1).toLowerCase()));
                    } catch (IllegalArgumentException ignored) {
                        Debugger.out("无效的engine值：" + engineMatcher.group(1) + "，有效值为：graaljs, rhino, default_engine, both");
                    }
                }
            }
        } catch (Exception ex) {
            Debugger.out("解析脚本元数据失败：" + info.location + " -> " + ex.getMessage());
        }
    }

    @Inject(method = "skipLoading", at = @At("RETURN"), cancellable = true)
    private void kubeLoader$applySideRules(CallbackInfoReturnable<String> cir) {
        if (!cir.getReturnValue().isEmpty()) {
            return;
        }

        var sides = ((ScriptFileInfoInterface) info).kubeLoader$getSides();
        if (sides.isEmpty()) {
            return;
        }

        String side = pack.manager.scriptType.name;
        boolean hasIncludeRule = false;

        for (String value : sides) {
            if (!value.startsWith("-")) {
                hasIncludeRule = true;
                if (value.equals(side)) {
                    return;
                }
            }
        }

        if (hasIncludeRule) {
            cir.setReturnValue("Script type '" + side + "' not in allowed sides: " + sides);
            return;
        }

        for (String value : sides) {
            if (value.startsWith("-") && value.substring(1).equals(side)) {
                cir.setReturnValue("Script type '" + side + "' is excluded by side: " + value);
                return;
            }
        }
    }

    @Inject(method = "load", at = @At("HEAD"), cancellable = true)
    public void kubeLoader$load(KubeJSContext cx, CallbackInfo ci) throws Throwable {
        // 检查脚本是否包含mixin属性
        Debugger.out("加载脚本 " + this.info.location);
        Debugger.out("mixin DSL Map: " + kubeLoader$getMixinDSL().keySet());
        KLScriptLoader.load(this.pack, this.info, this.lines, kubeLoader$getMixinDSL(), cx, ci);

    }

    private Map<String,List<MixinDSL>> kubeLoader$getMixinDSL() {
        return MixinManager.getMixinMap();
    }


}