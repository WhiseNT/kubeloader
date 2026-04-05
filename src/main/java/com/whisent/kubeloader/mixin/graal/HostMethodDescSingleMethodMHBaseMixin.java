package com.whisent.kubeloader.mixin.graal;

import com.oracle.truffle.api.nodes.Node;
import com.whisent.kubeloader.graal.accessor.ParameterTypeAccessor;
import com.whisent.kubeloader.graal.wrapper.GraalTypeWrappers;
import org.graalvm.polyglot.Value;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 这个也是留着没用, 懒得删了
@Mixin(targets = "com.oracle.truffle.host.HostMethodDesc$SingleMethod$MHBase", remap = false)
public abstract class HostMethodDescSingleMethodMHBaseMixin {

    @Inject(
        method = "invokeGuestToHost",
        at = @At("HEAD"),
        cancellable = false
    )
    private void kubeLoader$convertArguments(
            Object receiver,
            Object[] arguments,
            @Coerce Object cache,
            @Coerce Object hostContext,
            Node node,
            CallbackInfoReturnable<Object> cir
    ) {
        if (arguments == null || arguments.length == 0) return;
        Class<?>[] paramTypes = ((ParameterTypeAccessor) this).kubeLoader$getParameterTypes();
        if (paramTypes.length != arguments.length) return;

        for (int i = 0; i < arguments.length; i++) {
            Object arg = arguments[i];
            arguments[i] = GraalTypeWrappers.convertAny(arguments[i], paramTypes[i]);
        }
    }
}