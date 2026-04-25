package com.whisent.kubeloader.graal.wrapper;

import com.oracle.truffle.api.strings.TruffleString;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.util.KubeJSPlugins;
import dev.latvian.mods.rhino.util.wrap.TypeWrappers;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import com.whisent.kubeloader.Kubeloader;

import java.lang.reflect.Field;
import java.util.*;


public class GraalTypeWrappers {
    private static volatile TypeWrappers INSTANCE;
    private static final Object LOCK = new Object();

    private static TypeWrappers getInstance() {
        if (INSTANCE == null) {
            synchronized (LOCK) {
                if (INSTANCE == null) {
                    TypeWrappers tw = new TypeWrappers();
                    try {
                        for (KubeJSPlugin plugin : KubeJSPlugins.getAll()) {
                            try {
                                plugin.registerTypeWrappers(ScriptType.STARTUP, tw);
                            } catch (Exception e) {
                                Kubeloader.LOGGER.info("无法从Plugin注册Wrapper" + plugin.getClass().getName() + ":" + e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        Kubeloader.LOGGER.info("无法加载KubeJSPlugins的TypeWrappers: " + e.getMessage());
                    }
                    INSTANCE = tw;
                }
            }
        }
        return INSTANCE;
    }
    
    public static TypeWrappers get() {
        return getInstance();
    }
    
    public static void reload() {
        synchronized (LOCK) {
            INSTANCE = null;
        }
    }
    
    public static Object convertAny(Object arg, Class<?> targetType) {
        if (arg == null) return null;
        if (targetType.isInstance(arg)) return arg;
        Object javaObj = arg;
        if (arg instanceof Value v) {
            javaObj = valueToJavaObject(v);
        } else if (arg instanceof TruffleString ts) {
            javaObj = ts.toJavaStringUncached();
        }
        if (targetType.isInstance(javaObj)) return javaObj;
        TypeWrappers wrappers = getInstance();
        var factory = wrappers.getWrapperFactory(targetType, javaObj);
        if (factory != null) {
            try {
                Object result = factory.wrap(null, javaObj);
                if (result != null) return result;
            } catch (Exception e) {
                Kubeloader.LOGGER.info("TypeWrapper转换失败 " + targetType.getName() + ": " + e.getMessage());
            }
        }
        return javaObj;
    }
    
    @SuppressWarnings("unchecked")
    public static HostAccess.Builder registerTargetTypeMappings(HostAccess.Builder builder) {
        TypeWrappers wrappers = getInstance();
        Map<Class<?>, ?> wrappersMap = getWrappersMap(wrappers);
        if (wrappersMap == null || wrappersMap.isEmpty()) {
            Kubeloader.LOGGER.info("WARNING: 未找到可向GraalJS注册的TypeWrapper！");
            return builder;
        }
        
        int registered = 0;
        for (Map.Entry<Class<?>, ?> entry : wrappersMap.entrySet()) {
            Class<?> targetType = entry.getKey();
            if (targetType.isPrimitive() || targetType.isArray()) continue;
            if (targetType == Object.class) continue;
            if (targetType == String.class || targetType == CharSequence.class) continue;
            if (Number.class.isAssignableFrom(targetType)) continue;
            if (targetType == Boolean.class) continue;

            try {
                registerSingleMapping(builder, wrappers, targetType);
                registered++;
            } catch (Exception e) {
                Kubeloader.LOGGER.info("无法注册目标类型Mapping " + targetType.getName() + ": " + e.getMessage());
            }
        }
        return builder;
    }

    @SuppressWarnings("unchecked")
    private static <T> void registerSingleMapping(HostAccess.Builder builder, TypeWrappers wrappers, Class<T> targetType) {
        builder.targetTypeMapping(
            Object.class,
            targetType,
            (obj) -> {
                if (obj == null) return false;
                if (targetType.isInstance(obj)) return false;
                var factory = wrappers.getWrapperFactory(targetType, obj);
                return factory != null;
            },
            (obj) -> {
                var factory = wrappers.getWrapperFactory(targetType, obj);
                if (factory != null) {
                    try {
                        Object result = factory.wrap(null, obj);
                        if (result != null) return targetType.cast(result);
                    } catch (ClassCastException cce) {
                        Object result2 = factory.wrap(null, obj);
                        if (targetType.isInstance(result2)) return targetType.cast(result2);
                    } catch (Exception e) {
                        Kubeloader.LOGGER.info("TypeWrapper转换错误" + obj.getClass().getName() + " → " + targetType.getName() + ": " + e.getMessage());
                    }
                }
                return null;
            },
            HostAccess.TargetMappingPrecedence.LOW
        );
    }
    
    @SuppressWarnings("unchecked")
    private static Map<Class<?>, ?> getWrappersMap(TypeWrappers wrappers) {
        try {
            Field wrappersField = TypeWrappers.class.getDeclaredField("wrappers");
            wrappersField.setAccessible(true);
            return (Map<Class<?>, ?>) wrappersField.get(wrappers);
        } catch (Exception e) {
            Kubeloader.LOGGER.info("无法访问TypeWrappers.wrappers字段: " + e.getMessage());
            return null;
        }
    }

    private static Object valueToJavaObject(Value v) {
        if (v.isHostObject()) {
            return v.asHostObject();
        }
        if (v.isString()) {
            return v.asString();
        }
        if (v.isNumber()) {
            if (v.fitsInByte()) return v.asByte();
            if (v.fitsInShort()) return v.asShort();
            if (v.fitsInInt()) return v.asInt();
            if (v.fitsInLong()) return v.asLong();
            if (v.fitsInFloat()) return v.asFloat();
            if (v.fitsInDouble()) return v.asDouble();
            return v.as(Number.class);
        }
        if (v.isBoolean()) {
            return v.asBoolean();
        }
        if (v.hasArrayElements()) {
            List<Object> list = new ArrayList<>((int) v.getArraySize());
            for (long i = 0; i < v.getArraySize(); i++) {
                list.add(valueToJavaObject(v.getArrayElement(i)));
            }
            return list;
        }
        if (v.hasMembers()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : v.getMemberKeys()) {
                map.put(key, valueToJavaObject(v.getMember(key)));
            }
            return map;
        }
        return v.toString();
    }
}
