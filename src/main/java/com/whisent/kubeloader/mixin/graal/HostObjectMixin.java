package com.whisent.kubeloader.mixin.graal;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.whisent.kubeloader.graal.KJSNameRemapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Mixin(targets = "com.oracle.truffle.host.HostObject", remap = false)
public class HostObjectMixin {
    @Shadow
    Object obj;
    
    @Inject(
            method = "readMember(Ljava/lang/String;Lcom/oracle/truffle/api/nodes/Node;Lcom/oracle/truffle/host/HostObject$LookupFieldNode;Lcom/oracle/truffle/host/HostObject$ReadFieldNode;Lcom/oracle/truffle/host/HostObject$LookupMethodNode;Lcom/oracle/truffle/host/HostObject$LookupInnerClassNode;Lcom/oracle/truffle/api/profiles/InlinedBranchProfile;)Ljava/lang/Object;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void readMember(
            String identifier, 
            Node node, 
            @Coerce Object lookupField, // HostObject$LookupFieldNode
            @Coerce Object readField, // HostObject$ReadFieldNode
            @Coerce Object lookupMethod, // HostObject$LookupMethodNode
            @Coerce Object lookupInnerClass, // HostObject$LookupInnerClassNode
            InlinedBranchProfile error, 
            CallbackInfoReturnable<Object> cir) {

        if (obj == null) return;
        final Class<?> clazz = obj.getClass();
        if (KJSNameRemapper.hasDirectMember(clazz, identifier)) return;
        if (KJSNameRemapper.resolveAnnotationRemap(clazz, identifier) != null) return;
        String getterName = KJSNameRemapper.resolveToGetter(clazz, identifier);
        if (getterName == null) return;

        try {
            Method getter = clazz.getMethod(getterName);
            Object result = getter.invoke(obj);
            cir.setReturnValue(result);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new RuntimeException("Getter " + getterName + " on " + clazz.getName() + " threw", cause);
        } catch (NoSuchMethodException | IllegalAccessException ignored) {}
    }
}
