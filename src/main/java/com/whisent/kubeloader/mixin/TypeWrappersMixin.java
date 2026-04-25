package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.compat.GraalJSCompat;
import dev.latvian.mods.kubejs.util.ConsoleJS;
import dev.latvian.mods.rhino.util.wrap.TypeWrapperFactory;
import dev.latvian.mods.rhino.util.wrap.TypeWrappers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to intercept KubeJS's TypeWrappers registration and sync with GraalJS TypeWrapperRegistry.
 * This ensures that all type wrappers registered for Rhino are also available in GraalJS.
 */
@Mixin(value = TypeWrappers.class, remap = false)
public class TypeWrappersMixin {
    
    /**
     * Intercept TypeWrappers.registerSimple and sync to GraalJS TypeWrapperRegistry
     */
    @Inject(method = "registerSimple*", at = @At("HEAD"))
    private void onRegisterSimple(Class target, TypeWrapperFactory.Simple factory, CallbackInfo ci) {
        this.registerGraalWrapper(target, factory, ci);
    }
    @Inject(method = "register*", at = @At("HEAD"))
    private void onRegister(Class target, TypeWrapperFactory factory, CallbackInfo ci) {
        this.registerGraalWrapper(target, factory, ci);
    }

    private void registerGraalWrapper(Class target, TypeWrapperFactory factory, CallbackInfo ci) {
        if (GraalJSCompat.canUseGraalJS) {
            try {
//                if (factory instanceof TypeWrapperFactory.Simple) {
//                    TypeWrapperFactory.Simple simpleFactory = (TypeWrapperFactory.Simple) factory;
//                    TypeWrapperRegistry.registerSimple(target, simpleFactory::wrapSimple);
//                } else {
//                    TypeWrapperRegistry.register(target, input -> factory.wrap(null, input));
//                }


            } catch (Exception e) {
                ConsoleJS.STARTUP.error("[KubeLoader] Failed to sync TypeWrapper for " + target.getSimpleName() + ": " + e.getMessage());
            }
        }

    }



}
