package com.whisent.kubeloader.impl.mixin;

import com.whisent.kubeloader.klm.dsl.MixinDSL;

import java.util.List;
import java.util.Map;

public interface ScriptManagerInterface {
    Map<String, List<MixinDSL>> getKubeLoader$mixinDSLs();
}

