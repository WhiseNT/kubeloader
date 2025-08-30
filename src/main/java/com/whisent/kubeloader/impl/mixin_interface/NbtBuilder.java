package com.whisent.kubeloader.impl.mixin_interface;

import dev.latvian.mods.kubejs.item.ItemBuilder;
import net.minecraft.nbt.CompoundTag;

public interface NbtBuilder {
    CompoundTag kubeLoader$getDefaultNbt();
    ItemBuilder kubeLoader$setDefaultNbt(CompoundTag nbt);

}
