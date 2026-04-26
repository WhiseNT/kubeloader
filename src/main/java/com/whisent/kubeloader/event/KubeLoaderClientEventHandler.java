package com.whisent.kubeloader.event;

import com.whisent.kubeloader.utils.KLUtil;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

public class KubeLoaderClientEventHandler {
    public static void init() {
        NeoForge.EVENT_BUS.addListener(KubeLoaderClientEventHandler::onLevelLoad);
    }

    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel().isClientSide()) {
            KLUtil.setClientRegistryAccess(event.getLevel().registryAccess());
        }
    }

}