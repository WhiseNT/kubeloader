package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.event.forgeevent.BlockEntityAddedEvent;
import com.whisent.kubeloader.event.forgeevent.BlockEntityRemovedEvent;
import com.whisent.kubeloader.event.kjs.BlockEntityEventJS;
import com.whisent.kubeloader.event.kjs.BlockEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(value = LevelChunk.class)
public class LevelChunkMixin {
    @Inject(
            method = "setBlockState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/LevelChunk;addAndRegisterBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;)V"
            ),
            locals = LocalCapture.CAPTURE_FAILHARD,
            require = 1
    )
    private void onAddBlockEntity(BlockPos p_62865_, BlockState p_62866_, boolean p_62867_, CallbackInfoReturnable<BlockState> cir, int i, LevelChunkSection levelchunksection, boolean flag, int j, int k, int l, BlockState blockstate, Block block, boolean flag1, boolean flag2, BlockEntity blockentity) {

        // 只有在 blockentity 是新创建的情况下才触发
        if (blockentity != null && blockentity.getLevel() == null) {
            Level level = ((LevelChunk)((Object)this)).getLevel();
            if (level.isClientSide) return;
            MinecraftForge.EVENT_BUS.post(new BlockEntityAddedEvent(blockentity,
                    level, p_62865_));
            BlockEntityEvents.BLOCK_ENTITY_ADDED.post(new BlockEntityEventJS(block,blockentity,level, p_62865_),block);
        }
    }

    @Inject(
            method = "setBlockState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/LevelChunk;removeBlockEntity(Lnet/minecraft/core/BlockPos;)V"
            ),
            locals = LocalCapture.CAPTURE_FAILHARD,
            require = 1
    )
    private void onRemoveBlockEntity(
            BlockPos pos, BlockState state, boolean isMoving,
            CallbackInfoReturnable<BlockState> cir,
            int i, LevelChunkSection section, boolean hadOnlyAir,
            int j, int k, int l, BlockState oldState, Block block,
            boolean flag1, boolean hadBlockEntity
    ) {
        if (hadBlockEntity && !oldState.is(state.getBlock())) {
            Level level = ((LevelChunk)((Object)this)).getLevel();
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                MinecraftForge.EVENT_BUS.post(new BlockEntityRemovedEvent(be, level, pos));
                BlockEntityEvents.BLOCK_ENTITY_REMOVED.post(new BlockEntityEventJS(block,be, level, pos),block);
            }
        }
    }


}
