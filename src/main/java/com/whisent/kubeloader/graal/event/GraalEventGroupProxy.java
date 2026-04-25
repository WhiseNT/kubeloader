package com.whisent.kubeloader.graal.event;

import dev.latvian.mods.kubejs.event.EventGroupWrapper;
import dev.latvian.mods.kubejs.event.EventHandler;
import org.graalvm.polyglot.proxy.ProxyObject;

public class GraalEventGroupProxy implements ProxyObject {
    private final EventGroupWrapper wrapper;

    public GraalEventGroupProxy(EventGroupWrapper wrapper) {
        this.wrapper = wrapper;
    }

    @Override
    public Object getMember(String key) {
        EventHandler handler = (EventHandler) wrapper.get(key);
        if (handler == null) {
            return null;
        }
        return new GraalEventHandlerProxy(handler);
    }

    @Override
    public Object getMemberKeys() {
        return wrapper.keySet().toArray();
    }

    @Override
    public boolean hasMember(String key) {
        return wrapper.containsKey(key);
    }

    @Override
    public void putMember(String key, org.graalvm.polyglot.Value value) {
        throw new UnsupportedOperationException("Cannot set event handlers directly on event group");
    }
}
