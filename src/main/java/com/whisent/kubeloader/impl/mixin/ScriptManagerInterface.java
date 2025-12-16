package com.whisent.kubeloader.impl.mixin;

//import com.whisent.kubeloader.graal.context.IdentifiedContext;
import com.whisent.kubeloader.graal.context.IdentifiedContext;
import com.whisent.kubeloader.klm.dsl.MixinDSL;
import org.graalvm.polyglot.Context;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public interface ScriptManagerInterface {
    Map<String, List<MixinDSL>> getKubeLoader$mixinDSLs();
    Map<String, Object> getKubeLoader$bindings();
    Map<String, Context> getKubeLoader$scriptContexts();

}

