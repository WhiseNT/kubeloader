package com.whisent.kubeloader.mixin.graal;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.whisent.kubeloader.graal.KJSNameRemapper;

@Mixin(targets = "com.oracle.truffle.host.HostInteropReflect", remap = false)
public class HostInteropReflectMixin {
    @Inject(
            method = "findMethod(Lcom/oracle/truffle/host/HostContext;" +
                    "Ljava/lang/Class;Ljava/lang/String;Z)" +
                    "Lcom/oracle/truffle/host/HostMethodDesc;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void kubeLoader$findMethod(
            @Coerce Object context, // HostContext
            Class<?> clazz,
            String name,
            boolean onlyStatic,
            CallbackInfoReturnable<Object> cir) {

        if (cir.getReturnValue() != null) return;
        String resolved = KJSNameRemapper.resolveAnnotationRemap(clazz, name);
        if (resolved != null) {
            Object result = KJSNameRemapper.lookupHostMethod(context, clazz, resolved, onlyStatic);
            if (result != null) {
                cir.setReturnValue(result);
                return;
            }
        }
        String getter = KJSNameRemapper.resolveToGetter(clazz, name);
        if (getter != null) {
            Object result = KJSNameRemapper.lookupHostMethod(context, clazz, getter, onlyStatic);
            if (result != null) {
                cir.setReturnValue(result);
            }
        }
    }
    
    @Inject(
        method = "findField(Lcom/oracle/truffle/host/HostContext;Ljava/lang/Class;Ljava/lang/String;Z)Lcom/oracle/truffle/host/HostFieldDesc;",
        at = @At("RETURN"),
        cancellable = true,
        remap = false
    )
    private static void kubeLoader$findField(
            @Coerce Object context,
            Class<?> clazz,
            String name,
            boolean onlyStatic,
            CallbackInfoReturnable<Object> cir) {
        if (cir.getReturnValue() != null) return;
        String remapped = KJSNameRemapper.resolveFieldRemap(clazz, name);
        if (remapped != null && !remapped.equals(name)) {
            Object result = KJSNameRemapper.lookupHostField(context, clazz, remapped, onlyStatic);
            if (result != null) {
                cir.setReturnValue(result);
            }
        }
    }
    
    @Inject(
            method = "isReadable(Lcom/oracle/truffle/host/HostObject;" +
                    "Ljava/lang/Class;Ljava/lang/String;ZZ)Z",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void kubeLoader$isReadable(
            @Coerce Object object, // HostObject
            Class<?> clazz,
            String name,
            boolean onlyStatic,
            boolean isClass,
            CallbackInfoReturnable<Boolean> cir) {

        if (Boolean.TRUE.equals(cir.getReturnValue())) return;

        if (KJSNameRemapper.resolveAnnotationRemap(clazz, name) != null) {
            cir.setReturnValue(true);
            return;
        }
        if (KJSNameRemapper.resolveToGetter(clazz, name) != null) {
            cir.setReturnValue(true);
        }
    }
    
    @Inject(
            method = "isInvokable(Lcom/oracle/truffle/host/HostObject;" +
                    "Ljava/lang/Class;Ljava/lang/String;Z)Z",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void kubeLoader$isInvokable(
            @Coerce Object object, // HostObject
            Class<?> clazz,
            String name,
            boolean onlyStatic,
            CallbackInfoReturnable<Boolean> cir) {

        if (Boolean.TRUE.equals(cir.getReturnValue())) return;

        if (KJSNameRemapper.resolveAnnotationRemap(clazz, name) != null) {
            cir.setReturnValue(true);
            return;
        }
        if (KJSNameRemapper.resolveToGetter(clazz, name) != null) {
            cir.setReturnValue(true);
        }
    }
    
    @Inject(
            method = "isInternal(Lcom/oracle/truffle/host/HostObject;" +
                    "Ljava/lang/Class;Ljava/lang/String;Z)Z",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void kubeLoader$isInternal(
            @Coerce Object object, // HostObject
            Class<?> clazz,
            String name,
            boolean onlyStatic,
            CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.FALSE.equals(cir.getReturnValue())) return;
        if (KJSNameRemapper.resolveAnnotationRemap(clazz, name) != null) {
            cir.setReturnValue(false);
        }
    }
}
