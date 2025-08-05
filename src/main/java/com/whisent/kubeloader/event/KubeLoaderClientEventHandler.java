package com.whisent.kubeloader.event;

import com.whisent.kubeloader.utils.KLUtil;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.common.CommandPerformEvent;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.latvian.mods.kubejs.server.KubeJSServerEventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class KubeLoaderClientEventHandler {
    public static void init() {
        ClientLifecycleEvent.CLIENT_LEVEL_LOAD.register(KubeLoaderClientEventHandler::onClientLevelLoad);
    }
    public static void onClientLevelLoad(Level level) {
        KLUtil.setClientRegistryAccess(level.registryAccess());
    }
}
