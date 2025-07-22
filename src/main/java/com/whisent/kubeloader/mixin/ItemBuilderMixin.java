package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.impl.mixin_interface.NbtBuilder;
import dev.latvian.mods.kubejs.item.ItemBuilder;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(value = ItemBuilder.class)
public class ItemBuilderMixin implements NbtBuilder {
    @Unique
    public transient CompoundTag kubeLoader$nbt = null;

    @Override
    public CompoundTag getDefaultNbt() {
        return kubeLoader$nbt;
    }
    @Unique
    public ItemBuilder setDefaultNbt(CompoundTag nbt) {
        this.kubeLoader$nbt = nbt;
        return (ItemBuilder) ((Object)this);
    }


}