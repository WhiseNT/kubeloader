package com.whisent.kubeloader.mixin.graal;

import dev.latvian.mods.kubejs.recipe.RecipesEventJS;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Map;

@Mixin(value = RecipesEventJS.class, remap = false)
public abstract class RecipesEventJSMixin implements ProxyObject {
    @Shadow
    private Map<String, Object> recipeFunctions;

    @Override
    public Object getMember(String key) {
        var value = recipeFunctions.get(key);
        if (value != null) {
            return value;
        }

        return createMethodProxy(key);
    }

    @Override
    public Object getMemberKeys() {
        var keys = new LinkedHashSet<String>(recipeFunctions.keySet());
        for (var method : RecipesEventJS.class.getMethods()) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }

            if (Modifier.isPublic(method.getModifiers())) {
                keys.add(method.getName());
            }
        }

        return keys.toArray(String[]::new);
    }

    @Override
    public boolean hasMember(String key) {
        return recipeFunctions.containsKey(key) || findAccessibleMethod(key) != null;
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("RecipesEventJS is read-only");
    }

    private Object createMethodProxy(String name) {
        var method = findAccessibleMethod(name);
        if (method == null) {
            return null;
        }

        return (ProxyExecutable) arguments -> invokeMethod(method, arguments);
    }

    private Method findAccessibleMethod(String name) {
        for (var method : RecipesEventJS.class.getMethods()) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }

            if (Modifier.isPublic(method.getModifiers()) && method.getName().equals(name)) {
                return method;
            }
        }

        return null;
    }

    private Object invokeMethod(Method method, Value... arguments) {
        var convertedArguments = new Object[arguments.length];
        var parameterTypes = method.getParameterTypes();

        if (method.isVarArgs()) {
            throw new UnsupportedOperationException("Varargs are not supported for RecipesEventJS proxy calls: " + method.getName());
        }

        if (parameterTypes.length != arguments.length) {
            throw new IllegalArgumentException("No matching overload for " + method.getName() + " with " + arguments.length + " arguments");
        }

        for (int i = 0; i < arguments.length; i++) {
            convertedArguments[i] = convertArgument(arguments[i], parameterTypes[i]);
        }

        try {
            return method.invoke(this, convertedArguments);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot access RecipesEventJS method " + method.getName(), e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("RecipesEventJS method " + method.getName() + " threw", cause);
        }
    }

    private static Object convertArgument(Value value, Class<?> targetType) {
        if (value == null || value.isNull()) {
            if (targetType.isPrimitive()) {
                throw new IllegalArgumentException("Cannot convert null to " + targetType.getName());
            }
            return null;
        }

        var boxedType = boxType(targetType);

        try {
            if (boxedType == Object.class && value.isHostObject()) {
                return value.asHostObject();
            }

            return value.as(boxedType);
        } catch (ClassCastException | IllegalArgumentException | IllegalStateException ignored) {
            if (value.isHostObject()) {
                var hostObject = value.asHostObject();
                if (boxedType.isInstance(hostObject)) {
                    return hostObject;
                }
            }

            if (boxedType == String.class) {
                return value.toString();
            }

            throw new IllegalArgumentException("Cannot convert argument to " + targetType.getName());
        }
    }

    private static Class<?> boxType(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        } else if (type == boolean.class) {
            return Boolean.class;
        } else if (type == byte.class) {
            return Byte.class;
        } else if (type == char.class) {
            return Character.class;
        } else if (type == short.class) {
            return Short.class;
        } else if (type == int.class) {
            return Integer.class;
        } else if (type == long.class) {
            return Long.class;
        } else if (type == float.class) {
            return Float.class;
        } else if (type == double.class) {
            return Double.class;
        }

        return type;
    }
}