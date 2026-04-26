package com.whisent.kubeloader.event.kjs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.TargetedEventHandler;
import dev.latvian.mods.kubejs.plugin.builtin.event.ItemEvents;

public interface KubeLoaderEvents {
    EventGroup GROUP = EventGroup.of("KubeLoaderEvents");
    TargetedEventHandler ITEM_HURT = GROUP.server("itemHurt", () -> ItemHurtEventJS.class)
            .supportsTarget(ItemEvents.TARGET).hasResult();
    TargetedEventHandler RIGHT_CLICKED = GROUP.common("rightClicked", () -> KLRightclickedEventJS.class)
            .supportsTarget(ItemEvents.TARGET).hasResult();

    TargetedEventHandler TRIDENT_RELEASE_USING = GROUP.common("tridentReleased", () -> TridentReleased.class)
            .supportsTarget(ItemEvents.TARGET).hasResult();

}
