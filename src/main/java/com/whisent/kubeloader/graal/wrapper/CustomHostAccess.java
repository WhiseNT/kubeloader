package com.whisent.kubeloader.graal.wrapper;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

/**
 * Custom HostAccess implementation that dynamically converts JavaScript values
 * to Java types using TypeWrapperRegistry.
 * 
 * This implementation queries TypeWrapperRegistry for registered types
 * and creates targetTypeMapping for each registered type dynamically.
 */
public class CustomHostAccess {
    
    /**
     * Create a custom HostAccess that applies type wrappers for all registered types
     */
    public static HostAccess create() {
        HostAccess.Builder builder = HostAccess.newBuilder(HostAccess.ALL);
        
        // Dynamically register targetTypeMapping for all types in TypeWrapperRegistry
        for (Class<?> targetType : TypeWrapperRegistry.getRegisteredTypes()) {
            registerTypeMapping(builder, targetType);
        }
        
        System.out.println("[KubeLoader] CustomHostAccess registered " + 
                TypeWrapperRegistry.getRegisteredTypes().size() + " type mappings");
        
        return builder.build();
    }
    
    /**
     * Register a targetTypeMapping for a specific type
     */
    @SuppressWarnings("unchecked")
    private static <T> void registerTypeMapping(HostAccess.Builder builder, Class<T> targetType) {
        builder.targetTypeMapping(
                Value.class,
                targetType,
                // Accept: when value is convertible to target type
                v -> canConvert(v, targetType),
                // Convert: use TypeWrapperRegistry to perform conversion
                v -> (T) TypeWrapperRegistry.wrap(v, targetType),
                HostAccess.TargetMappingPrecedence.HIGH
        );
    }
    
    /**
     * Check if a Value can be converted to the target type.
     * This prevents incorrect conversions by only accepting values that make sense.
     */
    private static boolean canConvert(Value value, Class<?> targetType) {
        if (value == null || value.isNull()) {
            return false;
        }
        
        // If it's already a host object, check if it's the correct type
        if (value.isHostObject()) {
            Object hostObj = value.asHostObject();
            if (targetType.isInstance(hostObj)) {
                return true;
            }
            // Don't try to convert if it's already a host object of different type
            return false;
        }
        
        // IMPORTANT: Accept all string-like values (including TruffleString)
        // GraalVM may wrap strings as TruffleString internally
        if (value.isString()) {
            return true;
        }
        
        // Arrays can be converted to collection types
        if (value.hasArrayElements()) {
            return true;
        }
        
        // Numbers and booleans - only accept for primitive wrapper types
        if (value.isNumber()) {
            return Number.class.isAssignableFrom(targetType) ||
                   targetType == int.class || targetType == long.class ||
                   targetType == double.class || targetType == float.class;
        }
        
        if (value.isBoolean()) {
            return targetType == Boolean.class || targetType == boolean.class;
        }
        
        // Don't accept other cases to avoid incorrect conversions
        return false;
    }
}
