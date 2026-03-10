package com.whisent.kubeloader.mixin;

import com.machinezoo.noexception.WrappedException;
import com.oracle.truffle.js.runtime.JSException;
import com.whisent.kubeloader.compat.GraalJSCompat;
import com.whisent.kubeloader.graal.GraalApi;
import dev.latvian.mods.kubejs.event.*;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
@Mixin(value = EventHandlerContainer.class,remap = false)
public class EventHandlerContainerMixin {
    @Shadow
    EventHandlerContainer child;
    
    @Shadow
    public IEventHandler handler;

    /**
     * @author WhiseNT
     * @reason Support both Rhino and GraalJS event handlers
     */
    @Overwrite()
    public EventResult handle(EventJS event, EventExceptionHandler exh) throws EventExit {
        EventHandlerContainer itr = (EventHandlerContainer)((Object)this);

        do {
            try {
                // Check if this is a GraalJS handler (wrapped by GraalEventHandlerProxy)
                IEventHandler currentHandler = ((AccessEventHandlerContainer)itr).getHandler();
                
                // Directly call the handler - if it's from GraalJS, it will execute in GraalJS context
                // If it's from Rhino, it will execute in Rhino context
                currentHandler.onEvent(event);
                
            } catch (EventExit exit) {
                throw exit;
            } catch (Throwable ex) {
                if (GraalJSCompat.canUseGraalJS) {
                    GraalApi.throwException(ex,exh,event,itr);
                } else {
                    // 复制原始方法的异常处理逻辑
                    Throwable throwable;
                    WrappedException e;
                    for(throwable = ex; throwable instanceof WrappedException; throwable = e) {
                        e = (WrappedException)throwable;
                    }

                    if (throwable instanceof EventExit exit) {
                        throw exit;
                    }

                    if (exh == null || (throwable = exh.handle(event, itr, throwable)) != null) {
                        throw EventResult.Type.ERROR.exit(throwable);
                    }
                }
            }

            itr = ((AccessEventHandlerContainer)itr).getChild();
        }
        while (itr != null);

        return EventResult.PASS;

    }
}