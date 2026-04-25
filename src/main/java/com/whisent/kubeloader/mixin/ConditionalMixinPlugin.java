package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.ConfigManager;
import net.minecraftforge.fml.ModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class ConditionalMixinPlugin implements IMixinConfigPlugin {

    private static final String GRAALJS_MOD_ID = "graaljs";
    private static final String GRAAL_MOD_ID = "graal";
    private boolean isGraalJSLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        this.isGraalJSLoaded = ConfigManager.shouldUseModernJS();

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