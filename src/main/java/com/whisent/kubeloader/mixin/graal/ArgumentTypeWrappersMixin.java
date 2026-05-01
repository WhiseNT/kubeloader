package com.whisent.kubeloader.mixin.graal;

import dev.latvian.mods.kubejs.command.ArgumentTypeWrappers;
import dev.latvian.mods.kubejs.command.CommandRegistryEventJS;
import com.mojang.brigadier.arguments.ArgumentType;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ArgumentTypeWrappers.class, remap = false)
public abstract class ArgumentTypeWrappersMixin {

	public ArgumentType<?> create(Object event) {
		return ((ArgumentTypeWrappers) (Object) this).create((CommandRegistryEventJS) event);
	}
}