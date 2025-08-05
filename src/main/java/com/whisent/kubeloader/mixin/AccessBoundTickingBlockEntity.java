package com.whisent.kubeloader.mixin;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public interface AccessBoundTickingBlockEntity {
    @Accessor("blockEntity")
    BlockEntity getBlockEntity();
}
