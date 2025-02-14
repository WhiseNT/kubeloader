package com.whisent.kubeloader.files.events;

import com.whisent.kubeloader.Kubeloader;
import dev.latvian.mods.kubejs.client.ClientInitEventJS;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Kubeloader.MODID,value = Dist.CLIENT)
public class ClientEvent {

}
