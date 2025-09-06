package com.whisent.kubeloader.event;

import com.whisent.kubeloader.event.kjs.BlockEntityEventJS;
import com.whisent.kubeloader.event.kjs.BlockEntityEvents;
import com.whisent.kubeloader.event.kjs.KLRightclickedEventJS;
import com.whisent.kubeloader.event.kjs.KubeLoaderEvents;
import com.whisent.kubeloader.network.KLRightClickedEventPacket;
import com.whisent.kubeloader.network.NetworkHandler;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
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
    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.RightClickItem event) {
        //event.getEntity().sendSystemMessage(Component.literal(String.valueOf(event.getLevel().getGameTime())));
    }
    @SubscribeEvent
    public static void onPlayerInput(InputEvent.MouseButton event) {
        if (Minecraft.getInstance().screen == null) {

        }
    }

}
