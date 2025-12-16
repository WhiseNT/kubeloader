package com.whisent.kubeloader.impl.mixin;

import com.whisent.kubeloader.graal.DynamicGraalConsole;
import org.graalvm.polyglot.Context;

public interface GraalPack {
    void kubeLoader$setGraalContext(Context context);
    Context kubeLoader$getGraalContext();
    DynamicGraalConsole kubeLoader$getDynamicGraalConsole();
    void kubeLoader$setDynamicGraalConsole(DynamicGraalConsole console);
}
