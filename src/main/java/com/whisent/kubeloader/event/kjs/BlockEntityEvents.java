package com.whisent.kubeloader.event.kjs;

import dev.latvian.mods.kubejs.bindings.event.BlockEvents;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

public interface BlockEntityEvents {
    EventGroup GROUP = EventGroup.of("BlockEntityEvents");
    EventHandler BLOCK_ENTITY_ADDED = GROUP.server("added",
            () -> BlockEntityEventJS.class).extra(BlockEvents.SUPPORTS_BLOCK);
    EventHandler BLOCK_ENTITY_REMOVED = GROUP.server("removed",
            () -> BlockEntityEventJS.class).extra(BlockEvents.SUPPORTS_BLOCK);
    EventHandler BLOCK_ENTITY_LOADED = GROUP.server("loaded",
            () -> BlockEntityEventJS.class).extra(BlockEvents.SUPPORTS_BLOCK);
    EventHandler BLOCK_ENTITY_UNLOADED = GROUP.server("unloaded",
            () -> BlockEntityEventJS.class).extra(BlockEvents.SUPPORTS_BLOCK);
    EventHandler BLOCK_ENTITY_TICK = GROUP.common("tick",
            () -> BlockEntityEventJS.class).extra(BlockEvents.SUPPORTS_BLOCK);
}
