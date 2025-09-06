package com.whisent.kubeloader.network;

import com.whisent.kubeloader.event.kjs.ItemHurtEventJS;
import com.whisent.kubeloader.event.kjs.KLRightclickedEventJS;
import com.whisent.kubeloader.event.kjs.KubeLoaderEvents;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class KLRightClickedEventPacket {
    private final int hand;
    public KLRightClickedEventPacket(int hand) {
        this.hand = hand;
    }

    public void encode(FriendlyByteBuf buf) {

        buf.writeInt(hand);
    }

    public static KLRightClickedEventPacket decode(FriendlyByteBuf buf) {
        return new KLRightClickedEventPacket(buf.readInt());
    }
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        contextSupplier.get().enqueueWork(()->{
            contextSupplier.get().enqueueWork(() -> {
                if (contextSupplier.get().getDirection().getReceptionSide().isServer()) {
                    ServerPlayer player = contextSupplier.get().getSender();
                    InteractionHand interactionHand = hand == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                    ItemStack item = null;
                    if (player != null) {
                        item = player.getItemInHand(interactionHand);
                    }
                    if (item != null) {
                        if (KubeLoaderEvents.RIGHT_CLICKED.hasListeners()) {
                            EventResult result = KubeLoaderEvents.RIGHT_CLICKED.post(
                                    ScriptType.SERVER,
                                    item.getItem().asItem(),
                                    new KLRightclickedEventJS(player, interactionHand, item));
                            if (result.interruptFalse()) {
                                result.pass();
                            }
                        }
                    }

                }
            });
        });
    }
}
