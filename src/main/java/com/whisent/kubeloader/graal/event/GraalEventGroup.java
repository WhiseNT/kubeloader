package com.whisent.kubeloader.graal.event;

import dev.latvian.mods.kubejs.event.EventGroup;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.HashMap;
import java.util.Map;

public class GraalEventGroup implements ProxyObject {
    private final EventGroup eventGroup;
    private final Map<String,GraalEventHandler> handlers = new HashMap<>();
    public GraalEventGroup(EventGroup eventGroup) {
        this.eventGroup = eventGroup;
    }
    @Override
    public Object getMember(String key) {
        return (ProxyExecutable) arguments -> {
            if (arguments.length < 2) {
                subscribeEvent(eventGroup.name, key, arguments[1]);
            } else {
                var extraId = arguments[0];
                subscribeEvent(eventGroup.name, key, arguments[1]);
            }



            // 创建事件处理器
            GraalEventHandler handler = new GraalEventHandler(key);
            handlers.put(key, handler);

            return "事件注册成功: " + key;
        };
    }

    @Override
    public Object getMemberKeys() {
        return eventGroup.getHandlers().keySet().stream().toList();
    }

    @Override
    public boolean hasMember(String key) {
        return false;
    }

    @Override
    public void putMember(String key, Value value) {

    }

    private void subscribeEvent(String eventName, String eventId, Value callback) {

    }
}