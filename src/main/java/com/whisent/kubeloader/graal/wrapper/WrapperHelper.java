package com.whisent.kubeloader.graal.wrapper;

import com.whisent.kubeloader.graal.GraalApi;
import dev.latvian.mods.rhino.util.RemapForJS;
import dev.latvian.mods.rhino.util.RemapPrefixForJS;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
        
        if (obj instanceof org.graalvm.polyglot.Value ||
            obj instanceof org.graalvm.polyglot.proxy.ProxyObject ||
            obj instanceof org.graalvm.polyglot.proxy.ProxyArray ||
            obj instanceof org.graalvm.polyglot.proxy.ProxyExecutable) {
            return obj;
        }
        
        if (obj instanceof TypeConvertingProxyObject) {
            return ((TypeConvertingProxyObject) obj).getTarget();
        }
        
        Class<?> clazz = obj.getClass();
        
        if (clazz.isPrimitive() || 
            clazz == String.class || 
            clazz == Integer.class || 
            clazz == Long.class || 
            clazz == Double.class || 
            clazz == Float.class || 
            clazz == Boolean.class ||
            clazz == Byte.class ||
            clazz == Short.class ||
            clazz == Character.class ||
            obj instanceof Number ||
            obj instanceof java.util.Collection ||
            obj instanceof java.util.Map ||
            obj instanceof java.util.Iterator ||
            obj instanceof java.util.Enumeration ||
            obj instanceof java.util.Optional ||
            obj instanceof java.util.stream.Stream) {
            return obj;
        }
        
        return new TypeConvertingProxyObject(obj, obj.getClass());
    }
    
    public static Object unwrap(Object obj) {
        if (obj instanceof TypeConvertingProxyObject) {
            return ((TypeConvertingProxyObject) obj).getTarget();
        }
        return obj;
    }

    public static Object wrapReturnValue(Object result) {
        if (result == null) {
            return null;
        }
//        if (result instanceof org.graalvm.polyglot.Value ||
//            result instanceof org.graalvm.polyglot.proxy.ProxyObject ||
//            result instanceof org.graalvm.polyglot.proxy.ProxyArray ||
//            result instanceof org.graalvm.polyglot.proxy.ProxyExecutable) {
//            return result;
//        }
        
        if (result.getClass().isArray() || List.class.isAssignableFrom(result.getClass())) {
            return new TypeConvertingProxyObject(result, result.getClass());
        }
        
        return wrapObject(result);
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
    public static class TypeConvertingProxyObject implements ProxyObject, ProxyArray {
        
        private final Object target;
        private final Map<String, Method[]> methodCache;
        private final Map<String, Field> fieldCache;
        private final Map<String, TypeWrappingProxy> proxyCache;
        private final boolean isArray;
        private final boolean isList;
        private final boolean isMap;
        
        public TypeConvertingProxyObject(Object target, Class<?> clazz) {
            this.target = target;
            this.isArray = target.getClass().isArray();
            this.isList = target instanceof java.util.List;
            this.isMap = target instanceof java.util.Map;
            this.methodCache = new HashMap<>();
            this.fieldCache = new HashMap<>();
            this.proxyCache = new HashMap<>();
            
            if (!isArray && !isList && !isMap) {
                String remapPrefix = clazz.getAnnotation(RemapPrefixForJS.class) != null ? clazz.getAnnotation(RemapPrefixForJS.class).value() : null;
                for (Method method : target.getClass().getMethods()) {
                    String remappedMethodName = method.getName();
                    if (remapPrefix != null && remappedMethodName.startsWith(remapPrefix)) {
                        remappedMethodName = remappedMethodName.substring(remapPrefix.length());
                    }
                    remappedMethodName = method.isAnnotationPresent(RemapForJS.class) ? 
                        method.getAnnotation(RemapForJS.class).value() : remappedMethodName;
                    
                    addMethodToCache(remappedMethodName, method);
                    
                    if (remapPrefix != null) {
                        addMethodToCache(remapPrefix + remappedMethodName, method);
                    }
                    
                    if (method.getParameterCount() == 0) {
                        String getterName = "get" + Character.toUpperCase(remappedMethodName.charAt(0)) + remappedMethodName.substring(1);
                        addMethodToCache(getterName, method);

                    }
                }
                for (Field field : target.getClass().getFields()) {
                    String remappedFieldName = field.getName();
                    if (remapPrefix != null && remappedFieldName.startsWith(remapPrefix)) {
                        remappedFieldName = remappedFieldName.substring(remapPrefix.length());
                    }
                    remappedFieldName = field.isAnnotationPresent(RemapForJS.class) ?
                        field.getAnnotation(RemapForJS.class).value() : remappedFieldName;

                    fieldCache.put(remappedFieldName, field);


                }
            }
        }
        
        private void addMethodToCache(String key, Method method) {
            methodCache.computeIfAbsent(key, k -> new Method[0]);
            Method[] existing = methodCache.get(key);
            Method[] newArray = new Method[existing.length + 1];
            System.arraycopy(existing, 0, newArray, 0, existing.length);
            newArray[existing.length] = method;
            methodCache.put(key, newArray);
        }

        @Override
         public Object getMember(String key) {
             if (key == null) {
                 return null;
             }
             if (isMap) {
                 java.util.Map<?, ?> map = (java.util.Map<?, ?>) target;
                 if (map.containsKey(key)) {
                     Object value = map.get(key);
                     return wrapReturnValue(value);
                 }
             }

             if (!isArray && !isList) {
                TypeWrappingProxy cachedProxy = proxyCache.get(key);
                if (cachedProxy != null) {
                    return cachedProxy;
                }
                Method[] methods = methodCache.get(key);
                if (methods != null && methods.length > 0) {
                    TypeWrappingProxy newProxy = new TypeWrappingProxy(target, methods[0]);
                    proxyCache.put(key, newProxy);
                    return newProxy;
                }
                
                try {
                    var field = target.getClass().getField(key);
                    field.setAccessible(true);
                    Object value = field.get(target);
                    return wrapReturnValue(value);
                } catch (Exception e) {
                    return null;
                }
            }
            
            return null;
        }

        @Override
        public Object getMemberKeys() {
            if (isMap) {
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) target;
                return map.keySet().toArray();
            }
            if (isArray || isList) {
                java.util.List<String> keys = new java.util.ArrayList<>();
                keys.add("length");
                return keys.toArray();
            }

            return Stream.concat(
                    fieldCache.keySet().stream(),
                    methodCache.keySet().stream()
            ).toArray(String[]::new);
        }

        @Override
        public boolean hasMember(String key) {
            if (key == null) {
                return false;
            }
            if (isMap) {
                return ((java.util.Map<?, ?>) target).containsKey(key);
            }
            if (isArray || isList) {
                return "length".equals(key);
            }
            return methodCache.containsKey(key) || fieldCache.containsKey(key);
        }

        @Override
        public void putMember(String key, Value value) {
            if (isMap) {
                java.util.Map<Object, Object> map = (java.util.Map<Object, Object>) target;
                map.put(key, value.isHostObject() ? value.asHostObject() : value);
            }
        }

        @Override
        public Object get(long index) {
            if (isArray) {
                try {
                    Object element = java.lang.reflect.Array.get(target, (int) index);
                    return wrapReturnValue(element);
                } catch (Exception e) {
                    System.out.println("[KubeLoader] Array get error: " + e);
                    return null;
                }
            }
            if (isList) {
                try {
                    Object element = ((java.util.List<?>) target).get((int) index);
                    return wrapReturnValue(element);
                } catch (Exception e) {
                    System.out.println("[KubeLoader] List get error: " + e);
                    return null;
                }
            }
            return null;
        }
        
        @Override
        public void set(long index, Value value) {
            if (isArray) {
                try {
                    java.lang.reflect.Array.set(target, (int) index, 
                        value.isHostObject() ? value.asHostObject() : value);
                } catch (Exception e) {
                    // Ignore
                }
            }
            if (isList) {
                try {
                    ((java.util.List<Object>) target).set((int) index, 
                        value.isHostObject() ? value.asHostObject() : value);
                } catch (Exception e) {
                    // Ignore
                }
            }
        }

        @Override
        public long getSize() {
            if (isArray) {
                return java.lang.reflect.Array.getLength(target);
            }
            if (isList) {
                return ((java.util.List<?>) target).size();
            }
            return 0;
        }
        
        public Object getTarget() {
            return target;
        }

    }

}
