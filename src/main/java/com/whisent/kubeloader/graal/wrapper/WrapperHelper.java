package com.whisent.kubeloader.graal.wrapper;

import com.whisent.kubeloader.graal.GraalApi;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class to wrap Java objects with automatic type conversion support.
 * This enables transparent wrapper behavior in GraalJS similar to Rhino.
 */
public class WrapperHelper {
    
    /**
     * Wrap a Java object to provide automatic type conversion for method calls.
     * When JavaScript calls methods on the wrapped object, parameters are automatically
     * converted using the TypeWrapperRegistry.
     * 
     * @param obj The Java object to wrap
     * @return A proxy object with automatic type conversion
     */
    public static Object wrapObject(Object obj) {
        if (obj == null) {
            return null;
        }
        
        return new TypeConvertingProxyObject(obj);
    }
    
    /**
     * Register this helper as a global binding in the context
     * @param context The GraalJS context
     */
    public static void registerInContext(Context context) {
        context.getBindings("js").putMember("WrapperHelper", new WrapperHelperBindings());
    }
    
    /**
     * Bindings exposed to JavaScript
     */
    public static class WrapperHelperBindings {
        
        /**
         * Wrap an object for automatic type conversion
         */
        public Object wrap(Object obj) {
            return wrapObject(obj);
        }
        
        /**
         * Register a custom type wrapper from JavaScript
         */
        public void registerWrapper(Class<?> targetClass, Value wrapperFunction) {
            TypeWrapperRegistry.register(targetClass, value -> {
                if (wrapperFunction.canExecute()) {
                    Value result = wrapperFunction.execute(value);
                    if (result.isHostObject()) {
                        return result.asHostObject();
                    }
                }
                return null;
            });
        }
    }
    
    /**
     * Proxy object that intercepts method calls and applies type conversion
     */
    private static class TypeConvertingProxyObject implements ProxyObject {
        
        private final Object target;
        private final Map<String, Method[]> methodCache;
        
        public TypeConvertingProxyObject(Object target) {
            this.target = target;
            this.methodCache = new HashMap<>();
            
            // Cache all public methods
            for (Method method : target.getClass().getMethods()) {
                methodCache.computeIfAbsent(method.getName(), k -> new Method[0]);
                Method[] existing = methodCache.get(method.getName());
                Method[] newArray = new Method[existing.length + 1];
                System.arraycopy(existing, 0, newArray, 0, existing.length);
                newArray[existing.length] = method;
                methodCache.put(method.getName(), newArray);
            }
        }
        
        @Override
        public Object getMember(String key) {
            Method[] methods = methodCache.get(key);
            if (methods != null && methods.length > 0) {
                // Return a proxy executable that will handle type conversion
                return new TypeWrappingProxy(target, methods[0]);
            }
            
            // Try to get field value
            try {
                var field = target.getClass().getField(key);
                return field.get(target);
            } catch (Exception e) {
                return null;
            }
        }
        
        @Override
        public Object getMemberKeys() {
            return methodCache.keySet().toArray(new String[0]);
        }
        
        @Override
        public boolean hasMember(String key) {
            return methodCache.containsKey(key);
        }
        
        @Override
        public void putMember(String key, Value value) {
            // Try to set field value
            try {
                var field = target.getClass().getField(key);
                field.set(target, value.isHostObject() ? value.asHostObject() : value);
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
