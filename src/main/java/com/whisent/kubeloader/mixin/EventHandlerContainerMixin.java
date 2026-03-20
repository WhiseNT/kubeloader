package com.whisent.kubeloader.mixin;

import com.machinezoo.noexception.WrappedException;
import com.whisent.kubeloader.compat.GraalJSCompat;
import com.whisent.kubeloader.graal.GraalApi;
import dev.latvian.mods.kubejs.event.*;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = EventHandlerContainer.class,remap = false)
public class EventHandlerContainerMixin {
    @Shadow
    EventHandlerContainer child;
    
    @Shadow
    public IEventHandler handler;

    /**
     * Enhanced event handler that properly handles EventResult from GraalJS handlers
     * This injection modifies the handler calling logic to capture and process event results
     */
    @Inject(
        method = "handle",
        at = @At(
            value = "HEAD"
        ),
        cancellable = true
    )
    private void handleGraalJSEventWithResult(EventJS event, EventExceptionHandler exh, CallbackInfoReturnable<EventResult> cir) throws EventExit {
        if (!GraalJSCompat.canUseGraalJS()) {
            return; // Let original method handle Rhino handlers
        }

        EventHandlerContainer itr = (EventHandlerContainer)(Object)this;
        EventResult finalResult = EventResult.PASS;

        do {
            try {
                // Check if this is a GraalJS handler and handle it specially
                IEventHandler currentHandler = ((AccessEventHandlerContainer)itr).getHandler();

                // For GraalJS handlers, we need to capture the return value
                if (currentHandler instanceof com.whisent.kubeloader.graal.event.GraalEventHandlerProxy) {
                    // GraalJS handler - execute and capture result
                    Object result = currentHandler.onEvent(event);

                    // Process the result properly
                    if (result instanceof EventResult eventResult) {
                        // If we got an EventResult, update our final result
                        finalResult = eventResult;

                        // Handle interrupt conditions
                        if (eventResult.interruptFalse() || eventResult.interruptTrue()) {
                            cir.setReturnValue(eventResult);
                            return;
                        }
                    } else if (result instanceof Boolean booleanResult) {
                        // Handle boolean returns (true = pass, false = interrupt)
                        if (!booleanResult) {
                            // Create interrupt false result - need to find the right way to create it
                            // For now, we'll handle this in the final result processing
                            finalResult = null; // Will be handled below
                            cir.setReturnValue(finalResult);
                            return;
                        }
                    }
                    // For other return types (including null), continue with PASS
                } else {
                    // Rhino handler - use original logic
                    currentHandler.onEvent(event);
                }

            } catch (EventExit exit) {
                throw exit;
            } catch (Throwable ex) {
                if (GraalJSCompat.canUseGraalJS()) {
                    GraalApi.throwException(ex, exh, event, itr);
                } else {
                    // Original exception handling logic
                    Throwable throwable;
                    Throwable e;
                    for(throwable = ex; throwable instanceof WrappedException; throwable = e) {
                        e = throwable;
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
        while(itr != null);

        cir.setReturnValue(finalResult);
    }
}
