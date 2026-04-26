package com.whisent.kubeloader.mixin;

import dev.latvian.mods.rhino.util.wrap.TypeWrappers;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin to intercept KubeJS's TypeWrappers registration and sync with GraalJS TypeWrapperRegistry.
 * This ensures that all type wrappers registered for Rhino are also available in GraalJS.
 */
@Mixin(value = TypeWrappers.class, remap = false)
public class TypeWrappersMixin {
}
