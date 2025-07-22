package com.whisent.kubeloader.impl.mixin_interface;

import dev.latvian.mods.kubejs.item.ItemBuilder;
import net.minecraft.nbt.CompoundTag;

public interface NbtBuilder {
    CompoundTag getDefaultNbt();
    ItemBuilder setDefaultNbt(CompoundTag nbt);

}
