package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.compat.GraalJSCompat;
import com.whisent.kubeloader.graal.GraalApi;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.IEventHandler;
import dev.latvian.mods.kubejs.script.KubeJSContext;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ListJS;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Scriptable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for EventHandler to support GraalJS event registration.
 * 
 * This bridges GraalJS Value functions to KubeJS's IEventHandler interface,
 * allowing seamless event registration without knowing which engine is used.
 * 
 * Example usage in GraalJS:
 * ItemEvents.rightClicked('minecraft:diamond', event => {
 *     console.log('Diamond clicked!');
 *     event.cancel();
 * });
 */
@Mixin(value = EventHandler.class, remap = false)
public abstract class EventHandlerMixin {
    
    @Shadow
    public abstract void listen(Context cx, ScriptType type, Object extraId, IEventHandler handler);
    
    /**
     * Intercept EventHandler.call() to adapt GraalJS Value to IEventHandler.
     * 
     * Original call signature from KubeJS source:
     * public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args)
     * 
     * Handles both:
     * - ItemEvents.rightClicked(handler)  // No extraId
     * - ItemEvents.rightClicked('id', handler)  // With extraId
     * - ItemEvents.rightClicked(['id1', 'id2'], handler)  // Multiple extraIds
     */
    @Inject(
        method = "call",
        at = @At(
            value = "INVOKE",
            target = "Ldev/latvian/mods/kubejs/event/EventHandler;listen(Ldev/latvian/mods/rhino/Context;Ldev/latvian/mods/kubejs/script/ScriptType;Ljava/lang/Object;Ldev/latvian/mods/kubejs/event/IEventHandler;)V"
        ),
        cancellable = true
    )
    private void adaptGraalJSHandler(
        Context cx,
        Scriptable scope,
        Scriptable thisObj,
        Object[] args,
        CallbackInfoReturnable<Object> cir
    ) {
        if (!GraalJSCompat.canUseGraalJS()) {
            return;
        }
        ScriptType type = cx instanceof KubeJSContext kjsContext ? kjsContext.getType() : null;
        
        if (type == null) {
            return;  // Let original handle the error
        }
        
        try {
            // Parse arguments based on KubeJS's logic
            if (args.length == 1) {
                Object handler = args[0];
                
                if (GraalJSCompat.canUseGraalJS()) {
                    // GraalJS handler without extraId
                    IEventHandler adapted = GraalApi.createGraalHandler(handler, type);
                    listen(cx, type, null, adapted);
                    cir.setReturnValue(null);
                    return;
                }
            } else if (args.length == 2) {
                if (GraalJSCompat.canUseGraalJS()) {
                    Object extraIdArg = args[0];
                    Object handler = args[1];

                    IEventHandler adapted = GraalApi.createGraalHandler(handler, type);

                    // Support multiple extraIds (ListJS.orSelf)
                    for (Object extraId : ListJS.orSelf(extraIdArg)) {
                        listen(cx, type, extraId, adapted);
                    }

                    cir.setReturnValue(null);
                    return;
                }

            }
        } catch (Exception ex) {
            type.console.error("Failed to register GraalJS event handler: " + ex.getMessage());
            cir.setReturnValue(null);
        }
        
        // Not a GraalJS handler, let original method handle it (Rhino)
    }

}
