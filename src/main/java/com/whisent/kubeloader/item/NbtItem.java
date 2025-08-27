package com.whisent.kubeloader.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
@Deprecated
public class NbtItem  extends Item {
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
