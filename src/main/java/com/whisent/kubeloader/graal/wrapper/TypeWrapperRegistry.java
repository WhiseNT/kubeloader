//package com.whisent.kubeloader.graal.wrapper;
//
//import org.graalvm.polyglot.Value;
//
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.function.Function;
//import java.util.function.Predicate;
//
//// 这俩空壳真的还有留着的必要吗
//public class TypeWrapperRegistry {
//    private static final Map<Class<?>, TypeWrapperEntry<?>> WRAPPERS = new ConcurrentHashMap<>();
//
//    public static <T> void registerSimple(Class<T> targetClass, Function<Object, T> converter) {
//        WRAPPERS.put(targetClass, new TypeWrapperEntry<>(null, converter));
//    }
//
//    public static <T> void registerSimple(Class<T> targetClass, Predicate<Object> predicate, Function<Object, T> converter) {
//        WRAPPERS.put(targetClass, new TypeWrapperEntry<>(predicate, converter));
//    }
//
//    public static <T> void register(Class<T> targetClass, Function<Object, T> converter) {
//        registerSimple(targetClass, converter);
//    }
//
//    @SuppressWarnings("unchecked")
//    public static <T> Object wrap(Value value, Class<T> targetClass) {
//        if (value == null || value.isNull()) return null;
//
//        if (value.isHostObject()) {
//            Object hostObj = value.asHostObject();
//            if (targetClass.isInstance(hostObj)) return hostObj;
//        }
//
//        if (targetClass == String.class) {
//            return value.isString() ? value.asString() : value.toString();
//        }
//        if (targetClass == Integer.class || targetClass == int.class) {
//            return value.isNumber() ? value.asInt() : null;
//        }
//        if (targetClass == Long.class || targetClass == long.class) {
//            return value.isNumber() ? value.asLong() : null;
//        }
//        if (targetClass == Double.class || targetClass == double.class) {
//            return value.isNumber() ? value.asDouble() : null;
//        }
//        if (targetClass == Float.class || targetClass == float.class) {
//            return value.isNumber() ? value.asFloat() : null;
//        }
//        if (targetClass == Boolean.class || targetClass == boolean.class) {
//            return value.isBoolean() ? value.asBoolean() : null;
//        }
//        if (targetClass == Byte.class || targetClass == byte.class) {
//            return value.isNumber() ? value.asByte() : null;
//        }
//        if (targetClass == Short.class || targetClass == short.class) {
//            return value.isNumber() ? value.asShort() : null;
//        }
//        if (targetClass == Character.class || targetClass == char.class) {
//            return value.isString() && value.asString().length() == 1 ? value.asString().charAt(0) : null;
//        }
//
//        TypeWrapperEntry<?> exact = WRAPPERS.get(targetClass);
//        if (exact != null) {
//            Object input = valueToObject(value);
//            if (exact.predicate == null || exact.predicate.test(input)) {
//                Object result = exact.converter.apply(input);
//                if (result != null) return result;
//            }
//        }
//
//        for (Map.Entry<Class<?>, TypeWrapperEntry<?>> e : WRAPPERS.entrySet()) {
//            if (e.getKey() == targetClass) continue;
//            if (e.getKey().isAssignableFrom(targetClass)) {
//                Object input = valueToObject(value);
//                TypeWrapperEntry<?> entry = e.getValue();
//                if (entry.predicate == null || entry.predicate.test(input)) {
//                    Object result = entry.converter.apply(input);
//                    if (result != null) return result;
//                }
//            }
//        }
//
//        return value;
//    }
//
//    static Object valueToObject(Value value) {
//        if (value.isNull()) return null;
//        if (value.isHostObject()) return value.asHostObject();
//        if (value.isString()) return value.asString();
//        if (value.isNumber()) {
//            if (value.fitsInInt()) return value.asInt();
//            if (value.fitsInLong()) return value.asLong();
//            return value.asDouble();
//        }
//        if (value.isBoolean()) return value.asBoolean();
//        if (value.hasArrayElements()) {
//            int size = (int) value.getArraySize();
//            ArrayList<Object> list = new ArrayList<>(size);
//            for (int i = 0; i < size; i++) {
//                list.add(valueToObject(value.getArrayElement(i)));
//            }
//            return list;
//        }
//        if (value.hasMembers()) {
//            HashMap<String, Object> map = new HashMap<>();
//            for (String key : value.getMemberKeys()) {
//                map.put(key, valueToObject(value.getMember(key)));
//            }
//            return map;
//        }
//        return value;
//    }
//
//    public static void clear() {
//        WRAPPERS.clear();
//    }
//
//    public static Set<Class<?>> getRegisteredTypes() {
//        return Collections.unmodifiableSet(WRAPPERS.keySet());
//    }
//
//    private static class TypeWrapperEntry<T> {
//        final Predicate<Object> predicate;
//        final Function<Object, T> converter;
//
//        TypeWrapperEntry(Predicate<Object> predicate, Function<Object, T> converter) {
//            this.predicate = predicate;
//            this.converter = converter;
//        }
//    }
//}
