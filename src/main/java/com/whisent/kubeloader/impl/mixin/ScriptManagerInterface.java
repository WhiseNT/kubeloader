package com.whisent.kubeloader.impl.mixin;

import com.whisent.kubeloader.klm.dsl.MixinDSL;
import dev.latvian.mods.kubejs.util.ClassFilter;
import org.graalvm.polyglot.Context;

import java.util.List;
import java.util.Map;

public interface ScriptManagerInterface {
    Map<String, List<MixinDSL>> getKubeLoader$mixinDSLs();
    Map<String, Object> getKubeLoader$bindings();
    Map<String, Object> getKubeLoader$scriptContexts();

}

