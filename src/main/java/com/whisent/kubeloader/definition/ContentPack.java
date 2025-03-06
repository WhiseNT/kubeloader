package com.whisent.kubeloader.definition;

import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.resources.ResourceLocation;

/**
 * 一个 ContentPack 是 KubeJS 脚本（与资源）的集合，提供命名空间（{@link dev.latvian.mods.kubejs.script.ScriptPackInfo#namespace}
 * ）独立的脚本执行环境以及命名空间（{@link ResourceLocation#getNamespace()}）独立的资源集合
 *
 * @author ZZZank
 */
public interface ContentPack {

    String getNamespace();

    /**
     * 留作未来使用，或者可能也没用
     */
    default String getNamespace(ScriptType type) {
        return getNamespace();
    }

    ScriptPack getPack(ScriptType type);
}
