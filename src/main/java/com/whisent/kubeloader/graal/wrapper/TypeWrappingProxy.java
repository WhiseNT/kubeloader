//package com.whisent.kubeloader.graal.wrapper;
//
//import org.graalvm.polyglot.Value;
//import org.graalvm.polyglot.proxy.ProxyExecutable;
//
//import java.lang.invoke.MethodHandle;
//import java.lang.invoke.MethodHandles;
//import java.lang.reflect.Field;
//import java.lang.reflect.Method;
//import sun.misc.Unsafe;
//import sun.reflect.ReflectionFactory;
//
//public class TypeWrappingProxy implements ProxyExecutable {
//
//    private static final Unsafe UNSAFE;
//    private static final ReflectionFactory REFLECTION_FACTORY;
//
//    private final Object target;
//    private final Method method;
//    private final MethodHandle methodHandle;
//    private final Class<?>[] paramTypes;
//    private final int paramLen;
//
//    static {
//        try {
//            Field f = Unsafe.class.getDeclaredField("theUnsafe");
//            f.setAccessible(true);
//            UNSAFE = (Unsafe) f.get(null);
//            REFLECTION_FACTORY = ReflectionFactory.getReflectionFactory();
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    public TypeWrappingProxy(Object target, Method method) {
//        this.target = target;
//        this.method = method;
//        this.paramTypes = method.getParameterTypes();
//        this.paramLen = paramTypes.length;
//        try {
//            this.methodHandle = MethodHandles.lookup().unreflect(method).bindTo(target);
//        } catch (IllegalAccessException e) {
//            throw new RuntimeException("Cannot access method: " + method.getName(), e);
//        }
//    }
//
//    @Override
//    public Object execute(Value... arguments) {
//        int argLen = arguments.length;
//        Object[] wrappedArgs = argLen > paramLen ? new Object[argLen] : new Object[paramLen];
//
//        for (int i = 0; i < argLen; i++) {
//            if (i < paramLen) {
//                Class<?> paramType = paramTypes[i];
//                Object wrapped = TypeWrapperRegistry.wrap(arguments[i], paramType);
//
//                if (wrapped instanceof Value) {
//                    wrappedArgs[i] = convertValueToJava((Value) wrapped, paramType);
//                } else {
//                    wrappedArgs[i] = wrapped;
//                }
//            } else {
//                wrappedArgs[i] = arguments[i];
//            }
//        }
//
//        try {
//            return WrapperHelper.wrapReturnValue(methodHandle.invokeWithArguments(wrappedArgs));
//        } catch (Throwable t) {
//            throw new RuntimeException("Failed to invoke method " + method.getName(), t);
//        }
//    }
//
//    private Object convertValueToJava(Value value, Class<?> targetType) {
//        if (value.isNull()) {
//            return null;
//        }
//
//        if (value.isHostObject()) {
//            return value.asHostObject();
//        }
//
//        if (targetType == String.class && value.isString()) {
//            return value.asString();
//        }
//
//        if (targetType == int.class || targetType == Integer.class) {
//            if (value.isNumber()) {
//                return value.asInt();
//            }
//        }
//
//        if (targetType == long.class || targetType == Long.class) {
//            if (value.isNumber()) {
//                return value.asLong();
//            }
//        }
//
//        if (targetType == double.class || targetType == Double.class) {
//            if (value.isNumber()) {
//                return value.asDouble();
//            }
//        }
//
//        if (targetType == float.class || targetType == Float.class) {
//            if (value.isNumber()) {
//                return value.asFloat();
//            }
//        }
//
//        if (targetType == boolean.class || targetType == Boolean.class) {
//            if (value.isBoolean()) {
//                return value.asBoolean();
//            }
//        }
//
//        return value;
//    }
//}