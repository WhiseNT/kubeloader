package com.whisent.kubeloader.graal.wrapper;

import org.graalvm.polyglot.Value;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Registry for type wrappers that convert JavaScript values to Java types.
 * Mimics KubeJS's TypeWrappers mechanism for GraalJS compatibility.
 */
public class TypeWrapperRegistry {
    
    private static final Map<Class<?>, TypeWrapperEntry<?>> WRAPPERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, TypeWrapperEntry<?>[]> ASSIGNABLE_WRAPPER_CACHE = new ConcurrentHashMap<>();
    private static final TypeWrapperEntry<?>[] EMPTY_WRAPPER_ARRAY = new TypeWrapperEntry[0];
    
    /**
     * Register a simple type wrapper (like KubeJS's registerSimple)
     * @param targetClass The Java class this wrapper converts to
     * @param converter The conversion function
     */
    public static <T> void registerSimple(Class<T> targetClass, Function<Object, T> converter) {
        WRAPPERS.put(targetClass, new TypeWrapperEntry<>(null, converter));
    }
    
    /**
     * Register a type wrapper with predicate check (like KubeJS's registerSimple with predicate)
     * @param targetClass The Java class this wrapper converts to
     * @param predicate Checks if the value can be converted
     * @param converter The conversion function
     */
    public static <T> void registerSimple(Class<T> targetClass, Predicate<Object> predicate, Function<Object, T> converter) {
        WRAPPERS.put(targetClass, new TypeWrapperEntry<>(predicate, converter));
    }
    
    /**
     * Register a type wrapper (like KubeJS's register)
     * @param targetClass The Java class this wrapper converts to
     * @param converter The conversion function
     */
    public static <T> void register(Class<T> targetClass, Function<Object, T> converter) {
        registerSimple(targetClass, converter);
    }
    
    /**
     * Wrap a JavaScript value to the specified Java type
     */
    @SuppressWarnings("unchecked")
    public static <T> Object wrap(Value value, Class<T> targetClass) {
        if (value == null || value.isNull()) {
            return null;
        }
        
        if (value.isHostObject()) {
            Object hostObj = value.asHostObject();
            if (targetClass.isInstance(hostObj)) {
                return hostObj;
            }
        }
        
        if (!WRAPPERS.isEmpty()) {
            TypeWrapperEntry<?> entry = WRAPPERS.get(targetClass);
            if (entry != null) {
                Object input = valueToObject(value);
                if (entry.predicate == null || entry.predicate.test(input)) {
                    try {
                        Object result = entry.converter.apply(input);
                        if (result != null) {
                            return result;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            
            TypeWrapperEntry<?>[] assignableWrappers = ASSIGNABLE_WRAPPER_CACHE.get(targetClass);
            if (assignableWrappers == null) {
                assignableWrappers = buildAssignableWrappers(targetClass);
            }
            
            if (assignableWrappers.length > 0) {
                Object input = valueToObject(value);
                for (TypeWrapperEntry<?> wrapperEntry : assignableWrappers) {
                    if (wrapperEntry != entry && (wrapperEntry.predicate == null || wrapperEntry.predicate.test(input))) {
                        try {
                            Object result = wrapperEntry.converter.apply(input);
                            if (result != null) {
                                return result;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
        
        return value;
    }
    
    private static TypeWrapperEntry<?>[] buildAssignableWrappers(Class<?> targetClass) {
        List<TypeWrapperEntry<?>> list = null;
        for (Map.Entry<Class<?>, TypeWrapperEntry<?>> e : WRAPPERS.entrySet()) {
            if (e.getKey().isAssignableFrom(targetClass)) {
                if (list == null) {
                    list = new ArrayList<>(4);
                }
                list.add(e.getValue());
            }
        }
        if (list == null) {
            return ASSIGNABLE_WRAPPER_CACHE.putIfAbsent(targetClass, EMPTY_WRAPPER_ARRAY);
        }
        TypeWrapperEntry<?>[] arr = list.toArray(new TypeWrapperEntry[0]);
        ASSIGNABLE_WRAPPER_CACHE.put(targetClass, arr);
        return arr;
    }
    
    private static Object valueToObject(Value value) {
        if (value.isNull()) {
            return null;
        }
        if (value.isHostObject()) {
            return value.asHostObject();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isNumber()) {
            if (value.fitsInInt()) {
                return value.asInt();
            } else if (value.fitsInLong()) {
                return value.asLong();
            } else {
                return value.asDouble();
            }
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.hasArrayElements()) {
            int size = (int) value.getArraySize();
            ArrayList<Object> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(valueToObject(value.getArrayElement(i)));
            }
            return list;
        }
        if (value.hasMembers()) {
            HashMap<String, Object> map = new HashMap<>();
            for (String key : value.getMemberKeys()) {
                map.put(key, valueToObject(value.getMember(key)));
            }
            return map;
        }
        return value;
    }
    
    /**
     * Clear all registered wrappers (useful for testing)
     */
    public static void clear() {
        WRAPPERS.clear();
        ASSIGNABLE_WRAPPER_CACHE.clear();
    }
    
    /**
     * Get all registered wrapper target classes
     */
    public static Set<Class<?>> getRegisteredTypes() {
        return Collections.unmodifiableSet(WRAPPERS.keySet());
    }
    
    /**
     * Internal wrapper entry
     */
    private static class TypeWrapperEntry<T> {
        final Predicate<Object> predicate;
        final Function<Object, T> converter;
        
        TypeWrapperEntry(Predicate<Object> predicate, Function<Object, T> converter) {
            this.predicate = predicate;
            this.converter = converter;
        }
    }
}
