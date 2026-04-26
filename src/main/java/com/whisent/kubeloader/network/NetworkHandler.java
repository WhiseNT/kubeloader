package com.whisent.kubeloader.network;

import com.whisent.kubeloader.Kubeloader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = Kubeloader.MODID)
public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    static final CustomPacketPayload.Type<KLRightClickedEventPacket> RIGHT_CLICKED = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Kubeloader.MODID, "right_clicked")
    );

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var reg = event.registrar(PROTOCOL_VERSION).optional();
        reg.playToServer(RIGHT_CLICKED, KLRightClickedEventPacket.STREAM_CODEC, KLRightClickedEventPacket::handle);
    }

    public static void sendToServer(CustomPacketPayload msg) {
        PacketDistributor.sendToServer(msg);
    }

    public static void sendToAllClient(CustomPacketPayload msg, CustomPacketPayload... extra) {
        PacketDistributor.sendToAllPlayers(msg, extra);
    }
}
