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
        
        // If already a host object of the correct type, return as-is
        if (value.isHostObject() && targetClass.isInstance(value.asHostObject())) {
            return value.asHostObject();
        }
        
        // Convert Value to a more generic Object for the converter
        Object input = convertValueToObject(value);
        
        // Try registered wrapper for exact class
        TypeWrapperEntry<?> entry = WRAPPERS.get(targetClass);
        if (entry != null) {
            if (entry.predicate == null || entry.predicate.test(input)) {
                try {
                    Object result = entry.converter.apply(input);
                    if (result != null) {
                        return result;
                    }
                } catch (Exception e) {
                    // Silently continue to next wrapper
                }
            }
        }
        
        // Try wrappers for superclasses and interfaces
        for (Map.Entry<Class<?>, TypeWrapperEntry<?>> mapEntry : WRAPPERS.entrySet()) {
            if (mapEntry.getKey().isAssignableFrom(targetClass)) {
                TypeWrapperEntry<?> wrapperEntry = mapEntry.getValue();
                if (wrapperEntry.predicate == null || wrapperEntry.predicate.test(input)) {
                    try {
                        Object result = wrapperEntry.converter.apply(input);
                        if (result != null) {
                            return result;
                        }
                    } catch (Exception e) {
                        // Continue to next wrapper
                    }
                }
            }
        }
        
        // No wrapper found, return original input or value
        return input != null ? input : value;
    }
    
    /**
     * Convert GraalJS Value to a more generic Object that wrappers can handle
     */
    private static Object convertValueToObject(Value value) {
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
            } else if (value.fitsInDouble()) {
                return value.asDouble();
            }
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.hasArrayElements()) {
            long size = value.getArraySize();
            List<Object> list = new ArrayList<>();
            for (long i = 0; i < size; i++) {
                list.add(convertValueToObject(value.getArrayElement(i)));
            }
            return list;
        }
        if (value.hasMembers()) {
            Map<String, Object> map = new HashMap<>();
            for (String key : value.getMemberKeys()) {
                map.put(key, convertValueToObject(value.getMember(key)));
            }
            return map;
        }
        // Return the Value itself if we can't convert it
        return value;
    }
    
    /**
     * Clear all registered wrappers (useful for testing)
     */
    public static void clear() {
        WRAPPERS.clear();
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
