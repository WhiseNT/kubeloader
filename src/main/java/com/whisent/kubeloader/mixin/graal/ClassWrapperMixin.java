package com.whisent.kubeloader.mixin.graal;

import dev.latvian.mods.kubejs.util.ClassWrapper;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyInstantiable;
import graal.graalvm.polyglot.proxy.ProxyObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;

@Mixin(value = ClassWrapper.class, remap = false)
public abstract class ClassWrapperMixin implements ProxyObject, ProxyInstantiable {
	@Shadow
	public abstract Class<?> wrappedClass();

	@Override
	public Object getMember(String key) {
		var field = getStaticField(key);

		if (field != null) {
			try {
				return field.get(null);
			} catch (IllegalAccessException ignored) {
			}
		}

		if (hasStaticMethod(key)) {
			return (ProxyExecutable) arguments -> invokeStaticMethod(key, arguments);
		}

		return null;
	}

	@Override
	public Object getMemberKeys() {
		var keys = new LinkedHashSet<String>();
		var wrappedClass = wrappedClass();

		for (var field : wrappedClass.getFields()) {
			if (Modifier.isStatic(field.getModifiers())) {
				keys.add(field.getName());
			}
		}

		for (var method : wrappedClass.getMethods()) {
			if (Modifier.isStatic(method.getModifiers())) {
				keys.add(method.getName());
			}
		}

		return keys.toArray(String[]::new);
	}

	@Override
	public boolean hasMember(String key) {
		return getStaticField(key) != null || hasStaticMethod(key);
	}

	@Override
	public void putMember(String key, Value value) {
		throw new UnsupportedOperationException("ClassWrapper is read-only");
	}

	@Override
	public boolean removeMember(String key) {
		return false;
	}

	@Override
	public Object newInstance(Value... arguments) {
		var wrappedClass = wrappedClass();

		for (var constructor : wrappedClass.getConstructors()) {
			var convertedArguments = convertArguments(constructor.getParameterTypes(), constructor.isVarArgs(), arguments);

			if (convertedArguments != null) {
				try {
					return constructor.newInstance(convertedArguments);
				} catch (ReflectiveOperationException ignored) {
				}
			}
		}

		throw new UnsupportedOperationException("No matching constructor on " + wrappedClass.getName());
	}

	private Field getStaticField(String name) {
		try {
			var field = wrappedClass().getField(name);
			return Modifier.isStatic(field.getModifiers()) ? field : null;
		} catch (NoSuchFieldException ignored) {
			return null;
		}
	}

	private boolean hasStaticMethod(String name) {
		for (var method : wrappedClass().getMethods()) {
			if (method.getName().equals(name) && Modifier.isStatic(method.getModifiers())) {
				return true;
			}
		}

		return false;
	}

	private Object invokeStaticMethod(String name, Value... arguments) {
		for (var method : wrappedClass().getMethods()) {
			if (!method.getName().equals(name) || !Modifier.isStatic(method.getModifiers())) {
				continue;
			}

			var convertedArguments = convertArguments(method.getParameterTypes(), method.isVarArgs(), arguments);

			if (convertedArguments != null) {
				try {
					return method.invoke(null, convertedArguments);
				} catch (ReflectiveOperationException ignored) {
				}
			}
		}

		throw new UnsupportedOperationException("No matching static member '" + name + "' on " + wrappedClass().getName());
	}

	private Object[] convertArguments(Class<?>[] parameterTypes, boolean varArgs, Value[] arguments) {
		if (!varArgs) {
			if (parameterTypes.length != arguments.length) {
				return null;
			}

			Object[] convertedArguments = new Object[arguments.length];

			for (int i = 0; i < arguments.length; i++) {
				convertedArguments[i] = convertArgument(arguments[i], parameterTypes[i]);
			}

			return convertedArguments;
		}

		if (parameterTypes.length == 0 || arguments.length < parameterTypes.length - 1) {
			return null;
		}

		Object[] convertedArguments = new Object[parameterTypes.length];
		int lastIndex = parameterTypes.length - 1;

		for (int i = 0; i < lastIndex; i++) {
			convertedArguments[i] = convertArgument(arguments[i], parameterTypes[i]);
		}

		Class<?> componentType = parameterTypes[lastIndex].getComponentType();
		int varArgCount = arguments.length - lastIndex;
		Object varArgArray = Array.newInstance(componentType, varArgCount);

		for (int i = 0; i < varArgCount; i++) {
			Array.set(varArgArray, i, convertArgument(arguments[lastIndex + i], componentType));
		}

		convertedArguments[lastIndex] = varArgArray;
		return convertedArguments;
	}

	private Object convertArgument(Value value, Class<?> targetType) {
		if (value.isNull()) {
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

	@Override
	public String toString() {
		return "ClassWrapper[" + wrappedClass().getName() + "]";
	}
}