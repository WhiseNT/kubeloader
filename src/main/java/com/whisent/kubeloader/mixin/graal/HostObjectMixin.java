package com.whisent.kubeloader.mixin.graal;

import dev.latvian.mods.kubejs.event.EventExit;
import dev.latvian.mods.kubejs.event.KubeEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.whisent.kubeloader.graal.event.GraalEventSignal;
import com.whisent.kubeloader.graal.KJSNameRemapper;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;

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
        if (obj instanceof KubeEvent event && "cancel".equals(identifier)) {
            cir.setReturnValue((ProxyExecutable) arguments -> {
                if (arguments.length > 1) {
                    throw new IllegalArgumentException("cancel() accepts at most one argument");
                }

                try {
                    if (arguments.length == 0) {
                        return event.cancel(null);
                    }
                    return event.cancel(null, convertGraalValueToJava(arguments[0]));
                } catch (EventExit exit) {
                    throw new GraalEventSignal(exit.result);
                }
            });
            return;
        }
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

    private static Object convertGraalValueToJava(Value value) {
        if (value == null || value.isNull()) return null;
        if (value.isHostObject()) return value.asHostObject();
        if (value.isString()) return value.asString();
        if (value.isNumber()) {
            if (value.fitsInInt()) return value.asInt();
            if (value.fitsInLong()) return value.asLong();
            return value.asDouble();
        }
        if (value.isBoolean()) return value.asBoolean();
        if (value.hasArrayElements()) {
            long size = value.getArraySize();
            Object[] array = new Object[(int) size];
            for (int i = 0; i < size; i++) array[i] = convertGraalValueToJava(value.getArrayElement(i));
            return array;
        }
        return value;
    }
}