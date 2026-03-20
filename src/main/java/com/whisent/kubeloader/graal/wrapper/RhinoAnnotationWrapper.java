package com.whisent.kubeloader.graal.wrapper;

import dev.latvian.mods.rhino.util.RemapForJS;
import dev.latvian.mods.rhino.util.RemapPrefixForJS;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wrapper that makes Rhino @RemapForJS and @RemapPrefixForJS annotations work in GraalJS.
 * 
 * This class analyzes Java objects for Rhino annotations and creates a transparent proxy
 * that maps JS property names to Java methods/fields according to the annotations.
 * 
 * Usage:
 *   Object wrapped = RhinoAnnotationWrapper.wrap(javaObject);
 *   // Now JS can access methods using remapped names
 */
public class RhinoAnnotationWrapper {
    
    // Cache for class metadata to avoid repeated reflection
    private static final Map<Class<?>, ClassMetadata> CLASS_METADATA_CACHE = new ConcurrentHashMap<>();
    
    /**
     * Cached metadata for a class
     */
    private static class ClassMetadata {
        final boolean hasAnnotations;
        final Map<String, Method> methodsByName;  // Direct method name -> Method
        final Map<String, Method> jsNameToMethod; // JS name -> Method (for @RemapForJS)
        final Map<String, Field> fieldsByName;    // Direct field name -> Field
        final Map<String, Field> jsNameToField;   // JS name -> Field (for @RemapForJS)
        final Set<String> allMemberNames;
        
        ClassMetadata(Class<?> clazz) {
            this.methodsByName = new HashMap<>();
            this.jsNameToMethod = new HashMap<>();
            this.fieldsByName = new HashMap<>();
            this.jsNameToField = new HashMap<>();
            this.allMemberNames = new HashSet<>();
            
            // Build method maps
            for (Method method : clazz.getMethods()) {
                methodsByName.put(method.getName(), method);
                allMemberNames.add(method.getName());
                
                // Check for @RemapForJS
                RemapForJS remap = method.getAnnotation(RemapForJS.class);
                if (remap != null) {
                    jsNameToMethod.put(remap.value(), method);
                    allMemberNames.add(remap.value());
                }
            }
            
            // Handle @RemapPrefixForJS on class
            RemapPrefixForJS prefixRemap = clazz.getAnnotation(RemapPrefixForJS.class);
            if (prefixRemap != null) {
                String prefix = prefixRemap.value();
                for (Method method : clazz.getMethods()) {
                    if (method.getName().startsWith(prefix)) {
                        String jsName = method.getName().substring(prefix.length());
                        jsNameToMethod.put(jsName, method);
                        allMemberNames.add(jsName);
                    }
                }
            }
            
            // Handle @RemapPrefixForJS on interfaces
            buildMappingFromInterfaces(clazz, jsNameToMethod, allMemberNames);
            
            // Build field maps
            for (Field field : clazz.getFields()) {
                fieldsByName.put(field.getName(), field);
                allMemberNames.add(field.getName());
                
                // Check for @RemapForJS
                RemapForJS remap = field.getAnnotation(RemapForJS.class);
                if (remap != null) {
                    jsNameToField.put(remap.value(), field);
                    allMemberNames.add(remap.value());
                }
            }
            
            // Determine if has annotations
            this.hasAnnotations = !jsNameToMethod.isEmpty() || !jsNameToField.isEmpty();
        }
        
        private static void buildMappingFromInterfaces(Class<?> clazz, Map<String, Method> jsNameToMethod, Set<String> allMemberNames) {
            for (Class<?> iface : clazz.getInterfaces()) {
                RemapPrefixForJS ifacePrefixRemap = iface.getAnnotation(RemapPrefixForJS.class);
                if (ifacePrefixRemap != null) {
                    String prefix = ifacePrefixRemap.value();
                    for (Method method : clazz.getMethods()) {
                        if (method.getName().startsWith(prefix)) {
                            String jsName = method.getName().substring(prefix.length());
                            jsNameToMethod.put(jsName, method);
                            allMemberNames.add(jsName);
                        }
                    }
                }
                
                // Check interface methods for @RemapForJS
                for (Method method : iface.getMethods()) {
                    RemapForJS remap = method.getAnnotation(RemapForJS.class);
                    if (remap != null) {
                        // Find the actual implementing method
                        try {
                            Method implMethod = clazz.getMethod(method.getName(), method.getParameterTypes());
                            jsNameToMethod.put(remap.value(), implMethod);
                            allMemberNames.add(remap.value());
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                }
                
                // Recursively check super-interfaces
                buildMappingFromInterfaces(iface, jsNameToMethod, allMemberNames);
            }
        }
    }
    
    /**
     * Get or create cached metadata for a class
     */
    public static ClassMetadata getMetadata(Class<?> clazz) {
        return CLASS_METADATA_CACHE.computeIfAbsent(clazz, ClassMetadata::new);
    }
    
    /**
     * Wrap an object if it has Rhino annotations, otherwise return as-is.
     */
    public static Object wrap(Object obj) {
        if (obj == null) {
            return null;
        }
        
        if (obj instanceof org.graalvm.polyglot.Value ||
            obj instanceof org.graalvm.polyglot.proxy.ProxyObject ||
            obj instanceof org.graalvm.polyglot.proxy.ProxyExecutable) {
            return obj;
        }
        
        Class<?> clazz = obj.getClass();
        if (clazz.isArray() || 
            clazz.isPrimitive() || 
            clazz.getName().startsWith("java.lang.") ||
            clazz.getName().startsWith("java.util.")) {
            return obj;
        }

        // Get cached metadata
        ClassMetadata metadata = getMetadata(obj.getClass());
        
        // If no annotations, try WrapperHelper as fallback for $ prefix support
        if (metadata == null || !metadata.hasAnnotations) {
            return WrapperHelper.wrapObject(obj);
        }
        
        // Return a ProxyObject that handles name mapping using cached metadata
        return new ProxyObject() {
            @Override
            public Object getMember(String jsName) {
                // Check JS name mapping first (for @RemapForJS)
                Method mappedMethod = metadata.jsNameToMethod.get(jsName);
                if (mappedMethod != null) {
                    return createMethodProxy(mappedMethod, obj);
                }
                
                Field mappedField = metadata.jsNameToField.get(jsName);
                if (mappedField != null) {
                    try {
                        return wrap(mappedField.get(obj));
                    } catch (Exception e) {
                        return null;
                    }
                }
                
                // Check direct method access (non-annotated methods)
                Method directMethod = metadata.methodsByName.get(jsName);
                if (directMethod == null) {
                    String altKey = "$" + jsName;
                    directMethod = metadata.methodsByName.get(altKey);
                }
                if (directMethod != null) {
                    return createMethodProxy(directMethod, obj);
                }
                
                // Check direct field access
                Field directField = metadata.fieldsByName.get(jsName);
                if (directField != null) {
                    try {
                        return wrap(directField.get(obj));
                    } catch (Exception e) {
                        return null;
                    }
                }
                
                return null;
            }
            
            @Override
            public Object getMemberKeys() {
                return metadata.allMemberNames.toArray();
            }
            
            @Override
            public boolean hasMember(String jsName) {
                return metadata.allMemberNames.contains(jsName);
            }
            
            @Override
            public void putMember(String key, Value value) {
                throw new UnsupportedOperationException("Cannot modify wrapped object");
            }
        };
    }
    
    /**
     * Create a ProxyExecutable for a method
     */
    private static ProxyExecutable createMethodProxy(Method method, Object obj) {
        return (Value... args) -> {
            try {
                // Convert GraalJS values to Java objects with TypeWrapper conversion
                Class<?>[] paramTypes = method.getParameterTypes();
                Object[] javaArgs = new Object[method.getParameterCount()];
                
                for (int i = 0; i < javaArgs.length && i < args.length; i++) {
                    Value valueArg = args[i];
                    Class<?> expectedType = paramTypes[i];
                    
                    // First try TypeWrapperRegistry for automatic type conversion
                    // This includes String -> Component and all other KubeJS type wrappers
                    Object wrapped = TypeWrapperRegistry.wrap(valueArg, expectedType);
                    
                    // If TypeWrapper converted successfully, use it
                    if (wrapped != null && expectedType.isInstance(wrapped)) {
                        javaArgs[i] = wrapped;
                    } else {
                        // Fall back to basic conversion
                        javaArgs[i] = convertToJava(valueArg);
                    }
                }
                
                // Invoke the method
                Object result = method.invoke(obj, javaArgs);
                
                // Recursively wrap the result
                return wrap(result);
            } catch (Exception e) {
                throw new RuntimeException("Method invocation failed: " + method.getName(), e);
            }
        };
    }
    
    /**
     * Convert GraalJS Value to Java object
     */
    private static Object convertToJava(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isHostObject()) {
            return value.asHostObject();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isNumber()) {
            if (value.fitsInInt()) return value.asInt();
            if (value.fitsInLong()) return value.asLong();
            return value.asDouble();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        // For other Value types (arrays, objects, etc.), try to get as generic Object
        // This prevents returning the Value wrapper itself
        if (value.hasMembers() || value.hasArrayElements()) {
            return value.as(Object.class);
        }
        return value.as(Object.class);
    }
}