package com.whisent.kubeloader.event.kjs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.TargetedEventHandler;
import dev.latvian.mods.kubejs.plugin.builtin.event.BlockEvents;

public interface BlockEntityEvents {
    EventGroup GROUP = EventGroup.of("BlockEntityEvents");
    TargetedEventHandler BLOCK_ENTITY_ADDED = GROUP.server("added",
            () -> BlockEntityEventJS.class).supportsTarget(BlockEvents.TARGET);
    TargetedEventHandler BLOCK_ENTITY_REMOVED = GROUP.server("removed",
            () -> BlockEntityEventJS.class).supportsTarget(BlockEvents.TARGET);
    TargetedEventHandler BLOCK_ENTITY_LOADED = GROUP.server("loaded",
            () -> BlockEntityEventJS.class).supportsTarget(BlockEvents.TARGET);
    TargetedEventHandler BLOCK_ENTITY_UNLOADED = GROUP.server("unloaded",
            () -> BlockEntityEventJS.class).supportsTarget(BlockEvents.TARGET);
    TargetedEventHandler BLOCK_ENTITY_TICK = GROUP.common("tick",
            () -> BlockEntityEventJS.class).supportsTarget(BlockEvents.TARGET);
}
