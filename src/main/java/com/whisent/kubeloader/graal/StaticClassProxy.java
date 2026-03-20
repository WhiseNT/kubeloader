package com.whisent.kubeloader.graal;

import com.whisent.kubeloader.graal.wrapper.TypeWrapperRegistry;
import com.whisent.kubeloader.graal.wrapper.WrapperHelper;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * 为 Java 类创建静态方法代理，无需 @HostAccess.Export 注解
 * 
 * 使用方式：
 * <pre>
 * // Java 代码
 * registerBinding(ctx, "Text", TextWrapperWrapper.class);
 * 
 * // JavaScript 代码
 * let result = Text.of("Hello");  // 自动调用 TextWrapperWrapper.of()
 * </pre>
 */
public class StaticClassProxy implements ProxyObject {
    private final Class<?> targetClass;
    private final Map<String, List<Method>> staticMethods;
    
    public StaticClassProxy(Class<?> clazz) {
        this.targetClass = clazz;
        this.staticMethods = new HashMap<>();
        
        // 收集所有 public static 方法
        for (Method method : clazz.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers())) {
                staticMethods.computeIfAbsent(method.getName(), k -> new ArrayList<>()).add(method);
            }
        }
        
        System.out.println("[KubeLoader] StaticClassProxy for " + clazz.getSimpleName() + 
                " found " + staticMethods.size() + " static methods");
    }
    
    @Override
    public Object getMember(String key) {
        List<Method> methods = staticMethods.get(key);
        if (methods == null || methods.isEmpty()) {
            return null;
        }
        
        // 返回可执行的代理
        return (ProxyExecutable) args -> {
            // 尝试找到匹配的方法（根据参数数量）
            for (Method method : methods) {
                if (method.getParameterCount() == args.length) {
                    try {
                        // 转换参数 - 使用 TypeWrapperRegistry 支持类型转换
                        Object[] javaArgs = new Object[args.length];
                        Class<?>[] paramTypes = method.getParameterTypes();
                        
                        for (int i = 0; i < args.length; i++) {
                            javaArgs[i] = convertGraalValueToJava(args[i], paramTypes[i]);
                        }
                        
                        // 调用静态方法
                        Object result = method.invoke(null, javaArgs);
                        return WrapperHelper.wrapReturnValue(result);
                        
                    } catch (Exception e) {
                        // 继续尝试下一个重载
                        System.err.println("[KubeLoader] Failed to invoke " + method.getName() + 
                            " with param types: " + Arrays.toString(method.getParameterTypes()) + 
                            ": " + e.getMessage());
                    }
                }
            }
            
            throw new IllegalArgumentException("No matching method found for " + key + 
                    " with " + args.length + " arguments in class " + targetClass.getName());
        };
    }
    
    @Override
    public Object getMemberKeys() {
        return staticMethods.keySet().toArray(new String[0]);
    }
    
    @Override
    public boolean hasMember(String key) {
        return staticMethods.containsKey(key);
    }
    
    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("Cannot modify static class members");
    }
    
    /**
     * 将 GraalVM Value 转换为 Java 对象
     * 优先使用 TypeWrapperRegistry 进行类型转换（支持 String -> ItemStack 等）
     */
    private Object convertGraalValueToJava(Value value, Class<?> targetType) {
        if (value == null || value.isNull()) {
            return null;
        }
        
        // 优先：尝试使用 TypeWrapperRegistry 进行类型转换
        // 这样可以支持 String -> ItemStack, String -> Component 等 KubeJS 的类型包装
        try {
            Object wrapped = TypeWrapperRegistry.wrap(value, targetType);
            if (wrapped != null && targetType.isInstance(wrapped)) {
                return wrapped;
            }
        } catch (Exception e) {
            // TypeWrapper 转换失败，继续尝试其他方式
        }
        
        // Host 对象直接返回
        if (value.isHostObject()) {
            Object hostObj = value.asHostObject();
            if (targetType.isInstance(hostObj)) {
                return hostObj;
            }
        }
        
        // 字符串
        if (value.isString()) {
            String str = value.asString();
            if (targetType == String.class || targetType == CharSequence.class) {
                return str;
            }
        }
        
        // 数字
        if (value.isNumber()) {
            if (targetType == int.class || targetType == Integer.class) {
                return value.asInt();
            } else if (targetType == long.class || targetType == Long.class) {
                return value.asLong();
            } else if (targetType == double.class || targetType == Double.class) {
                return value.asDouble();
            } else if (targetType == float.class || targetType == Float.class) {
                return value.asFloat();
            } else if (targetType == short.class || targetType == Short.class) {
                return value.asShort();
            } else if (targetType == byte.class || targetType == Byte.class) {
                return value.asByte();
            }
        }
        
        // 布尔值
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        
        // 数组
        if (value.hasArrayElements()) {
            long size = value.getArraySize();
            Object[] array = new Object[(int) size];
            for (int i = 0; i < size; i++) {
                array[i] = convertGraalValueToJava(value.getArrayElement(i), Object.class);
            }
            return array;
        }
        
        // 默认：返回 Value 本身
        return value;
    }
}