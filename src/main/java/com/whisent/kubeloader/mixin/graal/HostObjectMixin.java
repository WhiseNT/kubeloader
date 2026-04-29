package com.whisent.kubeloader.mixin.graal;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.strings.TruffleString;

import dev.latvian.mods.kubejs.event.EventExit;
import dev.latvian.mods.kubejs.event.EventJS;
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
import java.util.ArrayList;
import java.util.List;

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

        final Class<?> clazz = obj.getClass();

        List<Method> enumMethods = findMethodsWithEnumParam(clazz, identifier);
        if (!enumMethods.isEmpty()) {
            cir.setReturnValue(createEnumProxy(obj, enumMethods));
            return;
        }

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

    // ===== 2. invokeMember：只预转换 Enum 参数，让原生逻辑继续 =====
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

        final Class<?> clazz = obj.getClass();

        for (Method m : clazz.getMethods()) {
            if (m.getDeclaringClass() == Object.class) continue;
            if (!m.getName().equals(name)) continue;
            if (m.getParameterCount() != args.length) continue;

            Class<?>[] paramTypes = m.getParameterTypes();

            boolean hasEnum = false;
            boolean canConvert = false;
            for (int i = 0; i < paramTypes.length; i++) {
                if (paramTypes[i].isEnum()) {
                    hasEnum = true;
                    Object arg = args[i];
                    if (arg instanceof String || arg instanceof TruffleString) {
                        canConvert = true;
                    }
                }
            }

            if (!hasEnum || !canConvert) continue;

            // 只修改 args 数组中的 Enum 参数，其他不动
            // 原生 invokeMember 会继续执行，用修改后的 args 匹配重载并调用
            for (int i = 0; i < args.length; i++) {
                if (paramTypes[i].isEnum()) {
                    Object converted = convertEnumArg(args[i], paramTypes[i]);
                    if (converted != args[i]) {
                        args[i] = converted;
                    }
                }
            }

            // 不设置返回值，不取消，让原生逻辑继续
            return;
        }
    }

    // ========== 以下辅助方法完全保留 ==========

    private static List<Method> findMethodsWithEnumParam(Class<?> clazz, String name) {
        List<Method> list = new ArrayList<>();
        for (Method m : clazz.getMethods()) {
            if (m.getDeclaringClass() == Object.class) continue;
            if (!m.getName().equals(name)) continue;
            for (Class<?> p : m.getParameterTypes()) {
                if (p.isEnum()) {
                    list.add(m);
                    break;
                }
            }
        }
        return list;
    }

    private static ProxyExecutable createEnumProxy(Object target, List<Method> candidates) {
        return arguments -> {
            int n = arguments.length;
            Method method = null;
            for (Method m : candidates) {
                if (m.getParameterCount() == n) {
                    method = m;
                    break;
                }
            }
            if (method == null) {
                throw new IllegalArgumentException(
                        "No overload of '" + candidates.get(0).getName() + "' accepts " + n + " arguments"
                );
            }
            Class<?>[] types = method.getParameterTypes();
            Object[] args = new Object[n];
            for (int i = 0; i < n; i++) {
                if (types[i].isEnum() && arguments[i].isString()) {
                    String enumName = arguments[i].asString().toUpperCase();
                    try {
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        Enum<?> e = Enum.valueOf((Class) types[i], enumName);
                        args[i] = e;
                    } catch (IllegalArgumentException ex) {
                        throw new IllegalArgumentException(
                                "Cannot convert '" + arguments[i].asString() + "' to enum " + types[i].getName()
                        );
                    }
                } else {
                    args[i] = convertGraalValueToJava(arguments[i]);
                }
            }
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                if (c instanceof EventExit ex) throw new GraalEventSignal(ex.result);
                if (c instanceof RuntimeException) throw (RuntimeException) c;
                throw new RuntimeException(c);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        };
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
}