package com.whisent.kubeloader.event.kjs;

import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ItemEntityEventJS extends EventJS {
    public final ItemEntity itemEntity;
    public final Level level;
    public final Vec3 pos;

    public ItemEntityEventJS(ItemEntity itemEntity, Level level, Vec3 pos) {
        this.itemEntity = itemEntity;
        this.level = level;
        this.pos = pos;
    }
}
