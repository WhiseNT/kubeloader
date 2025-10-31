package com.whisent.kubeloader.event.kjs;

import dev.latvian.mods.kubejs.bindings.event.ItemEvents;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

public interface KubeLoaderEvents {
    EventGroup GROUP = EventGroup.of("KubeLoaderEvents");
    EventHandler ITEM_HURT = GROUP.server("itemHurt",() -> ItemHurtEventJS.class)
            .extra(ItemEvents.SUPPORTS_ITEM).hasResult();
    EventHandler RIGHT_CLICKED = GROUP.common("rightClicked",() -> KLRightclickedEventJS.class)
            .extra(ItemEvents.SUPPORTS_ITEM).hasResult();

    EventHandler TRIDENT_RELEASE_USING = GROUP.common("tridentReleased",() -> TridentReleased.class)
            .extra(ItemEvents.SUPPORTS_ITEM).hasResult();

}
