package com.whisent.kubeloader.mixin;

import net.minecraftforge.fml.ModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class ConditionalMixinPlugin implements IMixinConfigPlugin {

    private static final String GRAALJS_MOD_ID = "graaljs";
    private boolean isGraalJSLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        // 通过 Forge ModList 检测 graaljs 模组是否实际加载
        this.isGraalJSLoaded = true;

        System.out.println("[KubeLoader] GraalJS mod loaded: " + isGraalJSLoaded);
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // 只有 graaljs 模组存在时才应用此 Mixin
        return isGraalJSLoaded;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}