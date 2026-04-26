package com.whisent.kubeloader.graal;

import dev.latvian.mods.rhino.util.RemapForJS;
import dev.latvian.mods.rhino.util.RemapPrefixForJS;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// 这个查找性能应该还可以再优化一下, 反正当前已经和Rhino性能差不多了, 等哪天闲的没事可以过来优化一下
public final class KJSNameRemapper {
    private static final ConcurrentHashMap<Class<?>, Map<String, String>> ANNOTATION_REMAP_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Map<String, String>> GETTER_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Set<String>> DIRECT_MEMBER_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Map<String, String>> FIELD_REMAP_CACHE = new ConcurrentHashMap<>();

    private static volatile java.lang.reflect.Method HOST_CLASS_DESC_FOR_CLASS;
    private static volatile java.lang.reflect.Method HOST_CLASS_DESC_LOOKUP_METHOD;
    private static volatile java.lang.reflect.Method HOST_CLASS_DESC_LOOKUP_FIELD;
    private static volatile boolean REFLECTION_INIT = false;

    private static synchronized void initReflection() {
        if (REFLECTION_INIT) return;
        REFLECTION_INIT = true;
        try {
            Class<?> ctxClass  = Class.forName("com.oracle.truffle.host.HostContext");
            Class<?> descClass = Class.forName("com.oracle.truffle.host.HostClassDesc");
            HOST_CLASS_DESC_FOR_CLASS = descClass.getDeclaredMethod("forClass", ctxClass, Class.class);
            HOST_CLASS_DESC_FOR_CLASS.setAccessible(true);

            HOST_CLASS_DESC_LOOKUP_METHOD = descClass.getDeclaredMethod("lookupMethod", String.class, boolean.class);
            HOST_CLASS_DESC_LOOKUP_METHOD.setAccessible(true);

            HOST_CLASS_DESC_LOOKUP_FIELD = descClass.getDeclaredMethod("lookupField", String.class, boolean.class);
            HOST_CLASS_DESC_LOOKUP_FIELD.setAccessible(true);
        } catch (Throwable ignored) {
        }
    }
    
    public static Object lookupHostMethod(Object context, Class<?> clazz, String javaName, boolean onlyStatic) {
        initReflection();
        if (HOST_CLASS_DESC_FOR_CLASS == null || HOST_CLASS_DESC_LOOKUP_METHOD == null) return null;
        try {
            Object desc = HOST_CLASS_DESC_FOR_CLASS.invoke(null, context, clazz);
            return HOST_CLASS_DESC_LOOKUP_METHOD.invoke(desc, javaName, onlyStatic);
        } catch (Throwable ignored) {
            return null;
        }
    }
    
    public static Object lookupHostField(Object context, Class<?> clazz, String javaName, boolean onlyStatic) {
        initReflection();
        if (HOST_CLASS_DESC_FOR_CLASS == null || HOST_CLASS_DESC_LOOKUP_FIELD == null) return null;
        try {
            Object desc = HOST_CLASS_DESC_FOR_CLASS.invoke(null, context, clazz);
            return HOST_CLASS_DESC_LOOKUP_FIELD.invoke(desc, javaName, onlyStatic);
        } catch (Throwable ignored) {
            return null;
        }
    }
    
    public static String resolveAnnotationRemap(Class<?> clazz, String jsName) {
        return ANNOTATION_REMAP_CACHE
                .computeIfAbsent(clazz, KJSNameRemapper::buildAnnotationRemapMap)
                .get(jsName);
    }
    
    public static String resolveToGetter(Class<?> clazz, String jsName) {
        if (hasDirectMember(clazz, jsName)) return null;
        return GETTER_CACHE
                .computeIfAbsent(clazz, KJSNameRemapper::buildGetterMap)
                .get(jsName);
    }
    
    public static boolean hasDirectMember(Class<?> clazz, String name) {
        return DIRECT_MEMBER_CACHE
                .computeIfAbsent(clazz, KJSNameRemapper::buildDirectMemberSet)
                .contains(name);
    }
    
    public static String resolveFieldRemap(Class<?> clazz, String jsName) {
        return FIELD_REMAP_CACHE
                .computeIfAbsent(clazz, KJSNameRemapper::buildFieldRemapMap)
                .get(jsName);
    }
    
    private static Map<String, String> buildAnnotationRemapMap(Class<?> clazz) {
        Map<String, String> map = new LinkedHashMap<>();
        Set<String> prefixes = collectAllPrefixes(clazz);
        for (Method method : clazz.getMethods()) {
            String javaName = method.getName();
            RemapForJS remapForJS = method.getAnnotation(RemapForJS.class);
            if (remapForJS != null) {
                map.put(remapForJS.value(), javaName);
            }
            for (String prefix : prefixes) {
                if (javaName.startsWith(prefix) && javaName.length() > prefix.length()) {
                    map.putIfAbsent(javaName.substring(prefix.length()), javaName);
                }
            }
        }
        processHierarchyAnnotations(clazz, map, prefixes);
        return Collections.unmodifiableMap(map);
    }
    
    private static Map<String, String> buildGetterMap(Class<?> clazz) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Method m : clazz.getMethods()) {
            if (m.getParameterCount() != 0) continue;
            String name = m.getName();
            if (name.length() > 3 && name.startsWith("get") && Character.isUpperCase(name.charAt(3))) {
                String prop = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                map.putIfAbsent(prop, name);
            } else if (name.length() > 2 && name.startsWith("is") && Character.isUpperCase(name.charAt(2))) {
                String prop = Character.toLowerCase(name.charAt(2)) + name.substring(3);
                map.putIfAbsent(prop, name);
            }
        }
        return Collections.unmodifiableMap(map);
    }
    
    private static Set<String> buildDirectMemberSet(Class<?> clazz) {
        Set<String> names = new HashSet<>();
        for (Field f : clazz.getFields()) names.add(f.getName());
        for (Method m : clazz.getMethods()) names.add(m.getName());
        return Collections.unmodifiableSet(names);
    }
    
    private static Map<String, String> buildFieldRemapMap(Class<?> clazz) {
        Map<String, String> map = new LinkedHashMap<>();
        Deque<Class<?>> queue = new ArrayDeque<>();
        queue.add(clazz);
        Set<Class<?>> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            Class<?> current = queue.poll();
            if (!visited.add(current)) continue;
            for (Field field : current.getDeclaredFields()) {
                String fieldName = field.getName();
                RemapForJS remap = field.getAnnotation(RemapForJS.class);
                if (remap != null) map.putIfAbsent(remap.value(), fieldName);
                map.putIfAbsent(fieldName, fieldName);
            }
            if (current.getSuperclass() != null) queue.add(current.getSuperclass());
            Collections.addAll(queue, current.getInterfaces());
        }
        return Collections.unmodifiableMap(map);
    }
    
    private static Set<String> collectAllPrefixes(Class<?> clazz) {
        Set<String> prefixes = new LinkedHashSet<>();
        Deque<Class<?>> queue   = new ArrayDeque<>();
        Set<Class<?>>   visited = new HashSet<>();
        queue.add(clazz);
        while (!queue.isEmpty()) {
            Class<?> c = queue.poll();
            if (!visited.add(c)) continue;
            RemapPrefixForJS ann = c.getAnnotation(RemapPrefixForJS.class);
            if (ann != null) prefixes.add(ann.value());
            if (c.getSuperclass() != null) queue.add(c.getSuperclass());
            Collections.addAll(queue, c.getInterfaces());
        }
        return prefixes;
    }
    
    private static void processHierarchyAnnotations(Class<?> clazz, Map<String, String> map, Set<String> prefixes) {
        Deque<Class<?>> queue   = new ArrayDeque<>();
        Set<Class<?>>   visited = new HashSet<>();
        if (clazz.getSuperclass() != null) queue.add(clazz.getSuperclass());
        Collections.addAll(queue, clazz.getInterfaces());
        while (!queue.isEmpty()) {
            Class<?> current = queue.poll();
            if (!visited.add(current)) continue;
            RemapPrefixForJS currentPrefix = current.getAnnotation(RemapPrefixForJS.class);
            for (Method m : current.getMethods()) {
                String javaName = m.getName();
                RemapForJS remap = m.getAnnotation(RemapForJS.class);
                if (remap != null && !map.containsKey(remap.value())) {
                    try {
                        Method impl = clazz.getMethod(javaName, m.getParameterTypes());
                        map.put(remap.value(), impl.getName());
                    } catch (NoSuchMethodException ignored) {}
                }
                if (currentPrefix != null) {
                    String prefix = currentPrefix.value();
                    if (javaName.startsWith(prefix) && javaName.length() > prefix.length()) {
                        String jsName = javaName.substring(prefix.length());
                        if (!map.containsKey(jsName)) {
                            try {
                                clazz.getMethod(javaName, m.getParameterTypes());
                                map.put(jsName, javaName);
                            } catch (NoSuchMethodException ignored) {}
                        }
                    }
                }
            }
            if (current.getSuperclass() != null) queue.add(current.getSuperclass());
            Collections.addAll(queue, current.getInterfaces());
        }
    }
    
    public static void clearCaches() {
        ANNOTATION_REMAP_CACHE.clear();
        GETTER_CACHE.clear();
        DIRECT_MEMBER_CACHE.clear();
        FIELD_REMAP_CACHE.clear();
    }
    
    public static void invalidate(Class<?> clazz) {
        ANNOTATION_REMAP_CACHE.remove(clazz);
        GETTER_CACHE.remove(clazz);
        DIRECT_MEMBER_CACHE.remove(clazz);
        FIELD_REMAP_CACHE.remove(clazz);
    }
}