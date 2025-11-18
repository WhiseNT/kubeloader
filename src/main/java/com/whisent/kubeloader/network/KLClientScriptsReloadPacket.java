package com.whisent.kubeloader.network;

import dev.latvian.mods.kubejs.KubeJS;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class KLClientScriptsReloadPacket {
    public KLClientScriptsReloadPacket() {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public static KLClientScriptsReloadPacket decode(FriendlyByteBuf buf) {
        return new KLClientScriptsReloadPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        if (contextSupplier.get().getDirection().getReceptionSide().isClient()) {
            KubeJS.PROXY.reloadClientInternal();
        }
    }
}
