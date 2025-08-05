package com.whisent.kubeloader.event;

import com.whisent.kubeloader.event.kjs.BlockEntityEvents;
import com.whisent.kubeloader.event.kjs.BlockEntityEventJS;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "kubeloader", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChunkEventHandler {
    @SubscribeEvent
    public static void onBlockEntityLoaded(ChunkEvent.Load event) {
        ChunkAccess chunk = event.getChunk();
        LevelAccessor level = event.getLevel();
        if (level.isClientSide() || !(chunk instanceof LevelChunk levelChunk)) return;
        for (BlockEntity be : levelChunk.getBlockEntities().values()) {
            Block block = be.getBlockState().getBlock();
            BlockEntityEvents.BLOCK_ENTITY_LOADED.post(new BlockEntityEventJS(block, be,(Level)level, be.getBlockPos()),block);
        }
    }
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        ChunkAccess chunk = event.getChunk();
        LevelAccessor level = event.getLevel();
        if (level.isClientSide() || !(chunk instanceof LevelChunk levelChunk)) return;
        for (BlockEntity be : levelChunk.getBlockEntities().values()) {
            Block block = be.getBlockState().getBlock();
            BlockEntityEvents.BLOCK_ENTITY_UNLOADED.post(new BlockEntityEventJS(block, be,(Level)level, be.getBlockPos()),block);
        }
    }
}
