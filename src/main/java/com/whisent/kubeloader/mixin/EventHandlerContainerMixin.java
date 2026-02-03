package com.whisent.kubeloader.mixin;

import com.oracle.truffle.js.runtime.JSException;
import dev.latvian.mods.kubejs.event.*;
import org.graalvm.polyglot.PolyglotException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = EventHandlerContainer.class,remap = false)
public class EventHandlerContainerMixin {
    @Shadow
    EventHandlerContainer child;

    /**
     * @author WhiseNT
     * @reason use Graal
     */
    @Overwrite()
    public EventResult handle(EventJS event, EventExceptionHandler exh) throws EventExit {
        EventHandlerContainer itr = (EventHandlerContainer)((Object)this);

        do {
            try {
                itr.handler.onEvent(event);
            } catch (EventExit exit) {
                throw exit;
            } catch (Throwable ex) {
                var throwable = ex;

                // 处理GraalJS的异常包装
                while (throwable instanceof PolyglotException e) {
                    if (e.isGuestException() && e.getCause() != null) {
                        throwable = e.getCause();
                    } else if (e.isInternalError() && e.getCause() != null) {
                        throwable = e.getCause();
                    } else {
                        break;
                    }
                }

                // 处理JavaScript异常
                while (throwable instanceof JSException e) {
                    throwable = e.getCause();
                }

                if (throwable instanceof EventExit exit) {
                    throw exit;
                }

                if (exh == null || (throwable = exh.handle(event, itr, throwable)) != null) {
                    throw EventResult.Type.ERROR.exit(throwable);
                }
            }

            itr = ((AccessEventHandlerContainer)itr).getChild();
        }
        while (itr != null);

        return EventResult.PASS;

    }
}