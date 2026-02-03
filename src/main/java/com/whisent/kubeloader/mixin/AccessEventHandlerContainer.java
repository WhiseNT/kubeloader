package com.whisent.kubeloader.mixin;

import dev.latvian.mods.kubejs.event.EventHandlerContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EventHandlerContainer.class)
public interface AccessEventHandlerContainer {
    @Accessor("child")
    EventHandlerContainer getChild();
}
