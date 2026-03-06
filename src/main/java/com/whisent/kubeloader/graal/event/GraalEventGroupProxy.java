package com.whisent.kubeloader.graal.event;

import dev.latvian.mods.kubejs.event.EventGroupWrapper;
import dev.latvian.mods.kubejs.event.EventHandler;
import org.graalvm.polyglot.proxy.ProxyObject;

/**
 * Proxy for EventGroupWrapper to work with GraalJS.
 * 
 * This allows accessing KubeJS events in GraalJS:
 * ItemEvents.rightClicked(...) 
 * BlockEvents.placed(...)
 * 
 * The proxy delegates to the underlying EventGroupWrapper from KubeJS,
 * which returns EventHandler instances that can be called from JavaScript.
 */
public class GraalEventGroupProxy implements ProxyObject {
    private final EventGroupWrapper wrapper;
    
    public GraalEventGroupProxy(EventGroupWrapper wrapper) {
        this.wrapper = wrapper;
    }
    
    @Override
    public Object getMember(String key) {
        // Get the EventHandler from the wrapper
        // EventGroupWrapper.get() returns BaseFunction (EventHandler extends BaseFunction)
        EventHandler handler = (EventHandler) wrapper.get(key);
        
        if (handler == null) {
            return null;
        }
        
        // Wrap EventHandler in a GraalEventHandlerProxy to make it callable from GraalJS
        // EventHandler extends Rhino's BaseFunction, but GraalJS needs ProxyExecutable
        return new GraalEventHandlerProxy(handler);
    }
    
    @Override
    public Object getMemberKeys() {
        // Return available event names (e.g., ["rightClicked", "placed", ...])
        return wrapper.keySet().toArray();
    }
    
    @Override
    public boolean hasMember(String key) {
        // EventGroupWrapper.containsKey() always returns true by design
        // (it creates handlers on-demand)
        return wrapper.containsKey(key);
    }
    
    @Override
    public void putMember(String key, org.graalvm.polyglot.Value value) {
        // Events are read-only, no need to support setting
        throw new UnsupportedOperationException("Cannot set event handlers directly on event group");
    }
}
