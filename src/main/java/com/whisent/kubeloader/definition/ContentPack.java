package com.whisent.kubeloader.definition;

import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptPackInfo;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    /**
     * 如果该 ContentPack 没有{@link PackLoadingContext#type()} 对应的 {@link ScriptPack}，返回 {@code null}
     */
    @Nullable
    ScriptPack getPack(PackLoadingContext context);

    default ScriptPack createEmptyPack(PackLoadingContext context) {
        return new ScriptPack(context.manager(), new ScriptPackInfo(getNamespace(context), ""));
    }
}
