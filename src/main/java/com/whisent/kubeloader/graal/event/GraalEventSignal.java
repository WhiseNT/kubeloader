package com.whisent.kubeloader.graal.event;


import dev.latvian.mods.kubejs.event.EventExit;
import dev.latvian.mods.kubejs.event.EventResult;

public final class GraalEventSignal extends RuntimeException {
    private final EventResult result;

    public GraalEventSignal(EventResult result) {
        super("GraalJS event control flow", null, false, false);
        this.result = result;
    }

    public EventExit toEventExit() {
        return new EventExit(result);
    }
}