package com.whisent.kubeloader.event;

import com.whisent.kubeloader.event.forgeevent.BlockEntityAddedEvent;
import com.whisent.kubeloader.event.forgeevent.BlockEntityRemovedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "kubeloader", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TestEventHandler {
    @SubscribeEvent
    public static void onBlockEntityAdded(BlockEntityAddedEvent event) {

    }
    @SubscribeEvent
    public static void onBlockEntityRemoved(BlockEntityRemovedEvent event) {

    }

}
