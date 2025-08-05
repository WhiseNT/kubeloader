package com.whisent.kubeloader.event.forgeevent;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.eventbus.api.Event;

public class BlockEntityAddedEvent extends Event {
    private final BlockEntity blockEntity;
    private final Level level;
    private final BlockPos pos;

    public BlockEntityAddedEvent(BlockEntity blockEntity, Level level, BlockPos pos) {
        this.blockEntity = blockEntity;
        this.level = level;
        this.pos = pos;
    }

    public BlockEntity getBlockEntity() { return blockEntity; }
    public Level getLevel() { return level; }
    public BlockPos getPos() { return pos; }
}
