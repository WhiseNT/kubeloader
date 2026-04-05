package com.whisent.kubeloader.mixin.graal;

import com.whisent.kubeloader.graal.accessor.ParameterTypeAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// 留着没用, 懒得删了
@Mixin(targets = "com.oracle.truffle.host.HostMethodDesc$SingleMethod", remap = false)
public abstract class HostMethodDescSingleMethodAccessor implements ParameterTypeAccessor {

    @Shadow
    public abstract Class<?>[] getParameterTypes();

    @Override
    public Class<?>[] kubeLoader$getParameterTypes() {
        return getParameterTypes();
    }
}