package com.whisent.kubeloader.graal.command;

import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;
import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.Commands;

public final class CommandsProxy implements ProxyObject {
    @Override
    public Object getMember(String key) {
        if ("literal".equals(key)) {
            return (ProxyExecutable) arguments -> {
                if (arguments.length != 1) {
                    throw new IllegalArgumentException("Commands.literal requires exactly 1 argument");
                }

                return Commands.literal(asString(arguments[0]));
            };
        }

        if ("argument".equals(key)) {
            return (ProxyExecutable) arguments -> {
                if (arguments.length != 2) {
                    throw new IllegalArgumentException("Commands.argument requires exactly 2 arguments");
                }

                var name = asString(arguments[0]);
                var type = arguments[1];

                if (!type.isHostObject()) {
                    throw new IllegalArgumentException("Commands.argument requires a host argument type");
                }

                var host = type.asHostObject();
                return Commands.argument(name, (ArgumentType<?>) host);
            };
        }

        return null;
    }

    @Override
    public Object getMemberKeys() {
        return new String[]{"literal", "argument"};
    }

    @Override
    public boolean hasMember(String key) {
        return "literal".equals(key) || "argument".equals(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("Commands proxy is read-only");
    }

    @Override
    public boolean removeMember(String key) {
        return false;
    }

    private static String asString(Value value) {
        if (value == null || value.isNull()) {
            return "";
        }

        return value.isHostObject() ? String.valueOf(value.asHostObject()) : value.asString();
    }
}