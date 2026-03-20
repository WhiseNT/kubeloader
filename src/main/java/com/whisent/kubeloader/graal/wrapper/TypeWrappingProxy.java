package com.whisent.kubeloader.graal.wrapper;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * Proxy executable that automatically wraps JavaScript values to Java types
 * based on method parameter types. This enables Rhino-like wrapper behavior in GraalJS.
 */
public class TypeWrappingProxy implements ProxyExecutable {
    
    private final Object target;
    private final Method method;
    
    public TypeWrappingProxy(Object target, Method method) {
        this.target = target;
        this.method = method;
    }
    
    @Override
    public Object execute(Value... arguments) {
        Parameter[] parameters = method.getParameters();
        Object[] wrappedArgs = new Object[arguments.length];
        
        for (int i = 0; i < arguments.length; i++) {
            if (i < parameters.length) {
                Class<?> paramType = parameters[i].getType();
                // Use TypeWrapperRegistry to wrap the value
                Object wrapped = TypeWrapperRegistry.wrap(arguments[i], paramType);
                
                // If wrapper returned a Value (no conversion), try to convert to Java
                if (wrapped instanceof Value) {
                    wrappedArgs[i] = convertValueToJava((Value) wrapped, paramType);
                } else {
                    wrappedArgs[i] = wrapped;
                }
            } else {
                // Varargs or extra arguments
                wrappedArgs[i] = arguments[i];
            }
        }
        
        try {
            Object result = method.invoke(target, wrappedArgs);
            return WrapperHelper.wrapReturnValue(result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke method " + method.getName(), e);
        }
    }
    
    /**
     * Convert a GraalJS Value to a Java object based on the target type
     */
    private Object convertValueToJava(Value value, Class<?> targetType) {
        if (value.isNull()) {
            return null;
        }
        
        if (value.isHostObject()) {
            return value.asHostObject();
        }
        
        if (targetType == String.class && value.isString()) {
            return value.asString();
        }
        
        if (targetType == int.class || targetType == Integer.class) {
            if (value.isNumber()) {
                return value.asInt();
            }
        }
        
        if (targetType == long.class || targetType == Long.class) {
            if (value.isNumber()) {
                return value.asLong();
            }
        }
        
        if (targetType == double.class || targetType == Double.class) {
            if (value.isNumber()) {
                return value.asDouble();
            }
        }
        
        if (targetType == float.class || targetType == Float.class) {
            if (value.isNumber()) {
                return value.asFloat();
            }
        }
        
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (value.isBoolean()) {
                return value.asBoolean();
            }
        }
        
        // Default: return as-is
        return value;
    }
}