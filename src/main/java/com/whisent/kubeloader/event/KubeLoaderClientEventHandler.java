package com.whisent.kubeloader.event;

import com.whisent.kubeloader.utils.KLUtil;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class KubeLoaderClientEventHandler {
    public static void init() {
        ClientLifecycleEvent.CLIENT_LEVEL_LOAD.register(KubeLoaderClientEventHandler::onClientLevelLoad);
    }
    public static void onClientLevelLoad(Level level) {
        KLUtil.setClientRegistryAccess(level.registryAccess());
    }
}
