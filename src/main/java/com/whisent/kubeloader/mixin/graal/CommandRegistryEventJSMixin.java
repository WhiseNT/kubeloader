package com.whisent.kubeloader.mixin.graal;

import com.whisent.kubeloader.graal.command.CommandRegistryArgumentsProxy;
import com.whisent.kubeloader.graal.command.CommandsProxy;
import com.whisent.kubeloader.graal.command.BuiltinSuggestionsProxy;
import dev.latvian.mods.kubejs.command.CommandRegistryEventJS;
import com.mojang.brigadier.context.CommandContext;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;
import org.spongepowered.asm.mixin.Mixin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;

@Mixin(value = CommandRegistryEventJS.class, remap = false)
public abstract class CommandRegistryEventJSMixin implements ProxyObject {

    @Override
    public Object getMember(String key) {
        var field = findField(key);

        if (field != null) {
            try {
                return field.get(this);
            } catch (IllegalAccessException ignored) {
            }
        }

        if ("arguments".equals(key)) {
            return new CommandRegistryArgumentsProxy((CommandRegistryEventJS) (Object) this);
        }

        if ("commands".equals(key)) {
            return new CommandsProxy();
        }

        if ("builtinSuggestions".equals(key)) {
            return new BuiltinSuggestionsProxy();
        }

        var getter = findGetter(key);
        if (getter != null) {
            try {
                return getter.invoke(this);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }

        var method = findAccessibleMethod(key);
        if (method != null) {
            return (ProxyExecutable) arguments -> invokeMethod(method, arguments);
        }

        return null;
    }

    @Override
    public Object getMemberKeys() {
        var keys = new LinkedHashSet<String>();

        for (var field : CommandRegistryEventJS.class.getFields()) {
            if (Modifier.isPublic(field.getModifiers())) {
                keys.add(field.getName());
            }
        }

        for (var method : CommandRegistryEventJS.class.getMethods()) {
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
        return findField(key) != null || findGetter(key) != null || findAccessibleMethod(key) != null;
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("CommandRegistryEventJS is read-only");
    }

    @Override
    public boolean removeMember(String key) {
        return false;
    }

    private Field findField(String name) {
        try {
            var field = CommandRegistryEventJS.class.getField(name);
            return Modifier.isPublic(field.getModifiers()) ? field : null;
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private Method findAccessibleMethod(String name) {
        for (var method : CommandRegistryEventJS.class.getMethods()) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }

            if (Modifier.isPublic(method.getModifiers()) && method.getName().equals(name)) {
                return method;
            }
        }

        return null;
    }

    private Method findGetter(String name) {
        var suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        var getterName = "get" + suffix;

        for (var method : CommandRegistryEventJS.class.getMethods()) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }

            if (!Modifier.isPublic(method.getModifiers()) || method.getParameterCount() != 0) {
                continue;
            }

            if (method.getName().equals(getterName)) {
                return method;
            }

            if (method.getName().startsWith("is") && method.getName().length() > 2) {
                var beanName = Character.toLowerCase(method.getName().charAt(2)) + method.getName().substring(3);
                if (beanName.equals(name)) {
                    return method;
                }
            }
        }

        return null;
    }

    private Object invokeMethod(Method method, Value... arguments) {
        var convertedArguments = new Object[arguments.length];
        var parameterTypes = method.getParameterTypes();

        if (method.isVarArgs()) {
            throw new UnsupportedOperationException("Varargs are not supported for CommandRegistryEventJS proxy calls: " + method.getName());
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
            throw new RuntimeException("Cannot access CommandRegistryEventJS method " + method.getName(), e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("CommandRegistryEventJS method " + method.getName() + " threw", cause);
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