package com.whisent.kubeloader.definition;

import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptPackInfo;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 一个 ContentPack 是 KubeJS 脚本（与资源）的集合，提供命名空间（{@link dev.latvian.mods.kubejs.script.ScriptPackInfo#namespace}
 * ）独立的脚本执行环境以及命名空间（{@link ResourceLocation#getNamespace()}）独立的资源集合
 *
 * @author ZZZank
 */
public interface ContentPack {

    @NotNull
    String getNamespace();


    /**
     * 留作未来使用，或者可能也没用
     */
    @NotNull
    default String getNamespace(PackLoadingContext context) {
        return getNamespace();
    }

    default Map getConfig() {return getConfig();}

    default int getPriority() {return getPriority();}
    /**
     * 如果该 ContentPack 没有{@link PackLoadingContext#type()} 对应的 {@link ScriptPack}，返回 {@code null}
     */
    @Nullable
    ScriptPack getPack(PackLoadingContext context);

    @NotNull
    default ScriptPack postProcessPack(PackLoadingContext context, @NotNull ScriptPack pack) {
        pack.scripts.sort(null);
        return pack;
    }

    default ScriptPack createEmptyPack(PackLoadingContext context) {
        return new ScriptPack(context.manager(), new ScriptPackInfo(getNamespace(context), ""));
    }
}
