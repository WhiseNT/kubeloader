package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.event.kjs.BlockEntityEventJS;
import com.whisent.kubeloader.event.kjs.BlockEntityEvents;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public class BoundTickingBlockEntityMixin {
    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/BlockEntityTicker;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)V"
            )
    )
    public void tick(CallbackInfo ci) {
        BlockEntity blockEntity = getAccess().getBlockEntity();
        Level level = blockEntity.getLevel();
        Block block = blockEntity.getBlockState().getBlock();
        if (level == null || level.isClientSide) {
            BlockEntityEvents.BLOCK_ENTITY_TICK.post(ScriptType.CLIENT,
                    block,
                    new BlockEntityEventJS(block, blockEntity, level, blockEntity.getBlockPos()));
        } else {
            BlockEntityEvents.BLOCK_ENTITY_TICK.post(
                    ScriptType.SERVER,
                    block,
                    new BlockEntityEventJS(block, blockEntity, level, blockEntity.getBlockPos()));
        }



    }

    private AccessBoundTickingBlockEntity getAccess() {
        return (AccessBoundTickingBlockEntity) (Object) this;
    }
}
