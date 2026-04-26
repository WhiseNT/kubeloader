package com.whisent.kubeloader.event.kjs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;


public class ItemHurtEventJS implements KubeEvent {
    public final ItemStack item;
    public final Entity entity;
    public final int damage;

    public ItemHurtEventJS(ItemStack item, Entity entity, int damage) {
        this.item = item;
        this.entity = entity;
        this.damage = damage;
    }
}
