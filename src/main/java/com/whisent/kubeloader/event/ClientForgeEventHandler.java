package com.whisent.kubeloader.event;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.event.kjs.KLRightclickedEventJS;
import com.whisent.kubeloader.event.kjs.KubeLoaderEvents;
import com.whisent.kubeloader.network.KLRightClickedEventPacket;
import com.whisent.kubeloader.network.NetworkHandler;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Kubeloader.MODID, value = Dist.CLIENT)
public class ClientForgeEventHandler {
    private static long lastClickedTime = 0;
    @SubscribeEvent
    public  static void onPlayerRightClick(ClientTickEvent.Pre event) {
        if (Minecraft.getInstance().screen == null && Minecraft.getInstance().player != null) {
            long time = 0;
            if (Minecraft.getInstance().level != null) {
                time = Minecraft.getInstance().level.getGameTime();
            }
            if (time == lastClickedTime) return;
            if (Minecraft.getInstance().options.keyUse.isDown()) {
                lastClickedTime = time;
                LocalPlayer player = Minecraft.getInstance().player;
                if (!player.getMainHandItem().isEmpty()) {
                    if (KubeLoaderEvents.RIGHT_CLICKED.hasListeners()) {
                        EventResult result = KubeLoaderEvents.RIGHT_CLICKED.post(
                                ScriptType.CLIENT,
                                player.getMainHandItem().getItem().asItem(),
                                new KLRightclickedEventJS(player, InteractionHand.MAIN_HAND, player.getMainHandItem())
                        );
                        if (result.interruptFalse()) {
                            result.pass();
                        }
                    }
                    NetworkHandler.sendToServer(new KLRightClickedEventPacket(0));
                }
                if (!player.getOffhandItem().isEmpty()) {
                    if (KubeLoaderEvents.RIGHT_CLICKED.hasListeners()) {
                        EventResult result = KubeLoaderEvents.RIGHT_CLICKED.post(
                                ScriptType.CLIENT,
                                player.getMainHandItem().getItem().asItem(),
                                new KLRightclickedEventJS(player, InteractionHand.OFF_HAND, player.getOffhandItem()));
                    }
                    NetworkHandler.sendToServer(new KLRightClickedEventPacket(1));
                }

            }
        }
    }
}
