package com.whisent.kubeloader.event.kjs;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ItemEntityHurtEventJS extends ItemEntityEventJS{
    public final DamageSource damageSource;
    public float amount;
    public ItemEntityHurtEventJS(ItemEntity itemEntity, Level level, Vec3 pos, DamageSource damageSource,float amount) {
        super(itemEntity, level, pos);
        this.damageSource = damageSource;
        this.amount = amount;
    }
}
