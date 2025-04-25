package com.whisent.kubeloader.definition;

import com.whisent.kubeloader.definition.meta.PackMetaData;
import dev.latvian.mods.kubejs.script.ScriptPack;
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

    PackMetaData getMetaData();

    /**
     * 如果该 ContentPack 没有{@link PackLoadingContext#type()} 对应的 {@link ScriptPack}，返回 {@code null}
     */
    @Nullable
    ScriptPack getPack(PackLoadingContext context);

    @NotNull
    default String id() {
        return this.getMetaData().id();
    }

    @NotNull
    default ScriptPack postProcessPack(PackLoadingContext context, @NotNull ScriptPack pack) {
        pack.scripts.sort(null);
        return pack;
    }
}
