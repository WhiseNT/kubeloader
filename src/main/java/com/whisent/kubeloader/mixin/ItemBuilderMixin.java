package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.impl.mixin_interface.NbtBuilder;
import dev.latvian.mods.kubejs.item.ItemBuilder;
import dev.latvian.mods.rhino.util.RemapForJS;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ItemBuilder.class)
public class ItemBuilderMixin implements NbtBuilder {
    @Unique
    public transient CompoundTag kubeLoader$nbt = null;

    @Override
    @RemapForJS("getDefaultNbt")
    public CompoundTag kubeLoader$getDefaultNbt() {
        return kubeLoader$nbt;
    }
    @Unique
    @RemapForJS("setDefaultNbt")
    public ItemBuilder kubeLoader$setDefaultNbt(CompoundTag nbt) {
        this.kubeLoader$nbt = nbt;
        return (ItemBuilder) ((Object)this);
    }


}