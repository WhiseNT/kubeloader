package com.whisent.kubeloader.graal.event;

import dev.latvian.mods.kubejs.event.EventJS;
import dev.latvian.mods.kubejs.event.EventResult;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class GraalEventHandler implements ProxyExecutable {
    private final String eventName;
    private final List<EventListener> listeners;
    private boolean hasResult = false;

    public GraalEventHandler(String eventName) {
        this.eventName = eventName;
        this.listeners = new CopyOnWriteArrayList<>();
    }

    @Override
    public Object execute(Value... arguments) {
        if (arguments.length < 2) {
            throw new IllegalArgumentException("需要事件ID和回调函数");
        }

        String eventId = arguments[0].asString();
        Value callback = arguments[1];

        if (!callback.canExecute()) {
            throw new IllegalArgumentException("回调必须是函数");
        }

        listeners.add(new EventListener(eventId, callback));
        return this; // 支持链式调用
    }

    // 触发事件 - 使用KubeJS的EventResult
    public EventResult triggerEvent(EventJS event) {
        for (EventListener listener : listeners) {
            try {
                listener.callback.execute(event);

            } catch (Exception e) {
                System.err.println("事件处理器错误: " + e.getMessage());
            }
        }

        return EventResult.PASS; // 默认返回PASS
    }

    public GraalEventHandler hasResult() {
        this.hasResult = true;
        return this;
    }

    public boolean hasListeners() {
        return !listeners.isEmpty();
    }

    public void clearListeners() {
        listeners.clear();
    }

    public int getListenerCount() {
        return listeners.size();
    }

    private static class EventListener {
        final String eventId;
        final Value callback;

        EventListener(String eventId, Value callback) {
            this.eventId = eventId;
            this.callback = callback;
        }
    }
}