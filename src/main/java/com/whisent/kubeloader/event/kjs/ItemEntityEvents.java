package com.whisent.kubeloader.event.kjs;

import dev.latvian.mods.kubejs.bindings.event.ItemEvents;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

public interface ItemEntityEvents {
    EventGroup GROUP = EventGroup.of("ItemEntityEvents");
    EventHandler ITEM_ENTITY_HURT = GROUP.server("hurt", () ->ItemEntityHurtEventJS.class)
            .extra(ItemEvents.SUPPORTS_ITEM).hasResult();
    EventHandler ITEM_ENTITY_TICK = GROUP.common("tick", () ->ItemEntityEventJS.class)
            .extra(ItemEvents.SUPPORTS_ITEM);
    EventHandler ITEM_ENTITY_SPAWN = GROUP.server("spawned", () ->ItemEntityEventJS.class)
            .extra(ItemEvents.SUPPORTS_ITEM).hasResult();
    EventHandler ITEM_ENTITY_REMOVED = GROUP.server("removed", () ->ItemEntityEventJS.class)
            .extra(ItemEvents.SUPPORTS_ITEM);
}
