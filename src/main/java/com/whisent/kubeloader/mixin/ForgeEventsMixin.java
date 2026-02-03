package com.whisent.kubeloader.mixin;

import dev.latvian.mods.kubejs.forge.ForgeEventConsumer;
import dev.latvian.mods.kubejs.forge.ForgeEventWrapper;
import dev.latvian.mods.kubejs.util.ConsoleJS;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(value = ForgeEventWrapper.class,remap = false)
public class ForgeEventsMixin {
    @Inject(method = "onEvent", at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void onOnEvent(Object eventClass, ForgeEventConsumer consumer, CallbackInfoReturnable<Object> cir) {

    }
}
