package com.whisent.kubeloader.impl.mixin_interface;

import com.whisent.kubeloader.mixinjs.dsl.MixinDSL;

import java.util.List;
import java.util.Map;

public interface ScriptManagerInterface {
    Map<String, List<MixinDSL>> getKubeLoader$mixinDSLs();
}

