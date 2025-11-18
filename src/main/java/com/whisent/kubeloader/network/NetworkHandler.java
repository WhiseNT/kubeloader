package com.whisent.kubeloader.network;

import com.whisent.kubeloader.Kubeloader;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder.named(
                    new ResourceLocation(Kubeloader.MODID,"main"))
            .serverAcceptedVersions((version) -> true)
            .clientAcceptedVersions((version) -> true)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(KLRightClickedEventPacket.class,id)
                .encoder(KLRightClickedEventPacket::encode)
                .decoder(KLRightClickedEventPacket::decode)
                .consumerMainThread(KLRightClickedEventPacket::handle)
                .add();
        CHANNEL.messageBuilder(KLClientScriptsReloadPacket.class,id+1)
                .encoder(KLClientScriptsReloadPacket::encode)
                .decoder(KLClientScriptsReloadPacket::decode)
                .consumerMainThread(KLClientScriptsReloadPacket::handle)
                .add();

    }
    public static void sendToServer(Object msg) {
        CHANNEL.send(PacketDistributor.SERVER.noArg(),msg);
    }
    public static void sendToAllClient(Object msg) {
        CHANNEL.send(PacketDistributor.ALL.noArg(),msg);
    }
}
