package com.whisent.kubeloader.mixin;

import dev.latvian.mods.kubejs.forge.ForgeEventConsumer;
import dev.latvian.mods.kubejs.forge.ForgeEventWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(value = ForgeEventWrapper.class,remap = false)
public class ForgeEventsMixin {
    @Inject(method = "onEvent", at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void onOnEvent(Object eventClass, ForgeEventConsumer consumer, CallbackInfoReturnable<Object> cir) {

    }
}
