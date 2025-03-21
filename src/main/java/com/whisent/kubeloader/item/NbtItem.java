package com.whisent.kubeloader.item;

import dev.latvian.mods.kubejs.core.ItemKJS;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class NbtItem  extends Item implements ItemKJS {
    public CompoundTag nbt;
    public NbtItem(Properties properties, CompoundTag nbt) {
        super(properties);
        this.nbt = nbt;
    }
    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = new ItemStack(this);
        stack.setTag(this.nbt);
        return stack;
    }
}
