package com.whisent.kubeloader.item;

import dev.latvian.mods.kubejs.item.ItemBuilder;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public class NbtItemBuilder extends ItemBuilder {
    public CompoundTag nbt;
    public NbtItemBuilder(ResourceLocation i) {
        super(i);
    }
    @Info("Sets the item's nbt.")
    public ItemBuilder nbt(CompoundTag v) {
        this.nbt = v;
        return this;
    }
    @Override
    public NbtItem createObject() {
        return new NbtItem(new Item.Properties(),this.nbt);
    }

}
