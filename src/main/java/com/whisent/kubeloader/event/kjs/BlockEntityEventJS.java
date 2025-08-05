package com.whisent.kubeloader.event.kjs;

import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

public class BlockEntityEventJS extends EventJS {
    public final Block block;
    public final BlockEntity blockEntity;
    public final Level level;
    public final BlockPos pos;

    public BlockEntityEventJS(Block block, BlockEntity blockEntity, Level level, BlockPos pos) {
        this.block = block;
        this.blockEntity = blockEntity;
        this.level = level;
        this.pos = pos;
    }
    public Block getBlock() { return block; }
    public BlockEntity getBlockEntity() { return blockEntity; }
    public Level getLevel() { return level; }
    public BlockPos getPos() { return pos; }
}
