package com.whisent.kubeloader.network;

import com.whisent.kubeloader.event.kjs.KLRightclickedEventJS;
import com.whisent.kubeloader.event.kjs.KubeLoaderEvents;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.script.ScriptType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record KLRightClickedEventPacket(int hand) implements CustomPacketPayload {
    public static final StreamCodec<ByteBuf, KLRightClickedEventPacket> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(KLRightClickedEventPacket::new, KLRightClickedEventPacket::hand);

    @Override
    public Type<?> type() {
        return NetworkHandler.RIGHT_CLICKED;
    }

    public void handle(IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            ctx.enqueueWork(() -> {
                InteractionHand interactionHand = hand == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                ItemStack item = player.getItemInHand(interactionHand);

                if (!item.isEmpty() && KubeLoaderEvents.RIGHT_CLICKED.hasListeners()) {
                    EventResult result = KubeLoaderEvents.RIGHT_CLICKED.post(
                            ScriptType.SERVER,
                            item.getItem().asItem(),
                            new KLRightclickedEventJS(player, interactionHand, item)
                    );

                    if (result.interruptFalse()) {
                        result.pass();
                    }
                }
            });
        }
    }
}
