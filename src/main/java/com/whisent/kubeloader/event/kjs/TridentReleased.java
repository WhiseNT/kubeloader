package com.whisent.kubeloader.event.kjs;

import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class TridentReleased extends EventJS {
    public final ItemStack item;
    public final Entity entity;
    public final Level level;
    public final int duration;
    public final int riptideLevel;
    public TridentReleased(ItemStack item, Entity entity, Level level, int duration,int riptideLevel) {
        this.item = item;
        this.entity = entity;
        this.level = level;
        this.duration = duration;
        this.riptideLevel = riptideLevel;
    }
}
