package com.whisent.kubeloader.event.kjs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.TargetedEventHandler;
import dev.latvian.mods.kubejs.plugin.builtin.event.ItemEvents;

public interface ItemEntityEvents {
    EventGroup GROUP = EventGroup.of("ItemEntityEvents");
    TargetedEventHandler ITEM_ENTITY_HURT = GROUP.server("hurt", () -> ItemEntityHurtEventJS.class)
            .supportsTarget(ItemEvents.TARGET).hasResult();
    TargetedEventHandler ITEM_ENTITY_TICK = GROUP.common("tick", () -> ItemEntityEventJS.class)
            .supportsTarget(ItemEvents.TARGET);
    TargetedEventHandler ITEM_ENTITY_SPAWN = GROUP.server("spawned", () -> ItemEntityEventJS.class)
            .supportsTarget(ItemEvents.TARGET).hasResult();
    TargetedEventHandler ITEM_ENTITY_REMOVED = GROUP.server("removed", () -> ItemEntityEventJS.class)
            .supportsTarget(ItemEvents.TARGET);
}
