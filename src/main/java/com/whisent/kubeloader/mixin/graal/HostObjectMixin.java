package com.whisent.kubeloader.mixin.graal;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.strings.TruffleString;

import dev.latvian.mods.kubejs.event.EventExit;
import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.resources.ResourceLocation;
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
import dev.latvian.mods.kubejs.registry.RegistryInfo;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Mixin(targets = "com.oracle.truffle.host.HostObject", remap = false)
public class HostObjectMixin {
    @Shadow
    Object obj;

    // ===== 1. readMember：完全保留原有逻辑 =====
    @Inject(
            method = "readMember(Ljava/lang/String;Lcom/oracle/truffle/api/nodes/Node;Lcom/oracle/truffle/host/HostObject$LookupFieldNode;Lcom/oracle/truffle/host/HostObject$ReadFieldNode;Lcom/oracle/truffle/host/HostObject$LookupMethodNode;Lcom/oracle/truffle/host/HostObject$LookupInnerClassNode;Lcom/oracle/truffle/api/profiles/InlinedBranchProfile;)Ljava/lang/Object;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void readMember(
            String identifier,
            Node node,
            @Coerce Object lookupField,
            @Coerce Object readField,
            @Coerce Object lookupMethod,
            @Coerce Object lookupInnerClass,
            InlinedBranchProfile error,
            CallbackInfoReturnable<Object> cir) {

        if (obj == null) return;

        if (obj instanceof EventJS event && "cancel".equals(identifier)) {
            cir.setReturnValue((ProxyExecutable) arguments -> {
                if (arguments.length > 1) {
                    throw new IllegalArgumentException("cancel() accepts at most one argument");
                }
                try {
                    if (arguments.length == 0) {
                        return event.cancel();
                    }
                    return event.cancel(convertGraalValueToJava(arguments[0]));
                } catch (EventExit exit) {
                    throw new GraalEventSignal(exit.result);
                }
            });
            return;
        }

        final Class<?> clazz = getMemberTargetClass();

        if (KJSNameRemapper.hasDirectMember(clazz, identifier)) return;
        if (KJSNameRemapper.resolveAnnotationRemap(clazz, identifier) != null) return;
        String getterName = KJSNameRemapper.resolveToGetter(clazz, identifier);
        if (getterName == null) return;

        try {
            Method getter = clazz.getMethod(getterName);
            Object result = unwrapJavaToJsValue(getter.invoke(obj));
            cir.setReturnValue(result);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new RuntimeException("Getter " + getterName + " on " + clazz.getName() + " threw", cause);
        } catch (NoSuchMethodException | IllegalAccessException ignored) {}
    }

    // ===== 2. invokeMember：预转换 Enum / Registry 参数，让原生逻辑继续 =====
    @Inject(
            method = "invokeMember",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void onInvokeMember(String name,
                                Object[] args,
                                Node node,
                                @Coerce Object lookupMethod,
                                @Coerce Object executeMethod,
                                @Coerce Object lookupField,
                                @Coerce Object readField,
                                InteropLibrary fieldValues,
                                InlinedBranchProfile error,
                                CallbackInfoReturnable<Object> cir) {
        if (obj == null || args == null) return;

        final Class<?> clazz = getMemberTargetClass();

        for (Method m : clazz.getMethods()) {
            if (m.getDeclaringClass() == Object.class) continue;
            java.util.Set<String> methodNames = KJSNameRemapper.resolveMethodCandidates(clazz, name);
            if (!methodNames.contains(m.getName())) continue;
            if (m.getParameterCount() != args.length) continue;

            Class<?>[] paramTypes = m.getParameterTypes();
            if (convertArguments(args, paramTypes)) {
                return;
            }
        }
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

    private static Object convertEnumArg(Object arg, Class<?> targetType) {
        if (!targetType.isEnum()) return arg;

        String str = null;
        if (arg instanceof String) {
            str = (String) arg;
        } else if (arg instanceof TruffleString ts) {
            str = ts.toJavaStringUncached();
        }

        if (str != null) {
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Enum<?> e = Enum.valueOf((Class) targetType, str.toUpperCase());
                return e;
            } catch (IllegalArgumentException ignored) {
                // 转换失败，返回原值，让原生逻辑去报错
            }
        }
        return arg;
    }

    private static Object convertArgument(Object arg, Class<?> targetType) {
        Object rawArg = arg instanceof Value value ? convertGraalValueToJava(value) : arg;

        Object converted = convertEnumArg(rawArg, targetType);
        if (converted != rawArg) {
            return converted;
        }

        converted = convertRegistryArg(rawArg, targetType);
        if (converted != rawArg) {
            return converted;
        }

        if (targetType == ResourceLocation.class) {
            ResourceLocation id = toResourceLocation(rawArg);
            if (id != null) {
                return id;
            }
        }

        return rawArg;
    }

    private static Object convertRegistryArg(Object arg, Class<?> targetType) {
        if (arg == null) {
            return null;
        }

        ResourceLocation id = toResourceLocation(arg);
        if (id == null) {
            return arg;
        }

        Object resolved = resolveRegistryValueFromRegistryInfo(id, targetType);
        return resolved != null ? resolved : arg;
    }

    private static Object resolveRegistryValueFromRegistryInfo(ResourceLocation id, Class<?> targetType) {
        RegistryInfo<?> exactMatch = null;
        RegistryInfo<?> assignableMatch = null;

        for (RegistryInfo<?> info : RegistryInfo.MAP.values()) {
            Class<?> objectBaseClass = info.objectBaseClass;

            if (objectBaseClass == targetType) {
                exactMatch = info;
                break;
            }

            if (assignableMatch == null
                    && targetType != Object.class
                    && targetType.isAssignableFrom(objectBaseClass)) {
                assignableMatch = info;
            }
        }

        RegistryInfo<?> info = exactMatch != null ? exactMatch : assignableMatch;
        if (info == null) {
            return null;
        }

        Object value = info.getValue(id);
        return value != null && targetType.isInstance(value) ? value : null;
    }

    private static boolean convertArguments(Object[] args, Class<?>[] paramTypes) {
        Object[] convertedArgs = null;

        for (int i = 0; i < args.length; i++) {
            Object converted = convertArgument(args[i], paramTypes[i]);
            if (converted != args[i]) {
                if (convertedArgs == null) {
                    convertedArgs = args.clone();
                }
                convertedArgs[i] = converted;
            }
        }

        if (convertedArgs == null) {
            return false;
        }

        System.arraycopy(convertedArgs, 0, args, 0, args.length);
        return true;
    }

    private static ResourceLocation toResourceLocation(Object arg) {
        if (arg instanceof ResourceLocation id) {
            return id;
        }
        if (arg instanceof String str) {
            ResourceLocation id = ResourceLocation.tryParse(str);
            if (id != null) {
                return id;
            }

            if (str.indexOf(':') == -1) {
                return ResourceLocation.tryParse("minecraft:" + str);
            }

            return null;
        }
        if (arg instanceof TruffleString ts) {
            return toResourceLocation(ts.toJavaStringUncached());
        }
        return null;
    }

    private static Object unwrapJavaToJsValue(Object value) {
        return value;
    }

    private Class<?> getMemberTargetClass() {
        return obj.getClass();
    }
}