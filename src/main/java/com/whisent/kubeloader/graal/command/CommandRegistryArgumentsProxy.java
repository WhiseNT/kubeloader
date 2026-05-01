package com.whisent.kubeloader.graal.command;

import dev.latvian.mods.kubejs.command.ArgumentTypeWrappers;
import dev.latvian.mods.kubejs.command.CommandRegistryEventJS;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import com.mojang.brigadier.context.CommandContext;

import java.util.Arrays;

public final class CommandRegistryArgumentsProxy implements ProxyObject {
    private final CommandRegistryEventJS event;

    public CommandRegistryArgumentsProxy(CommandRegistryEventJS event) {
        this.event = event;
    }

    @Override
    public Object getMember(String key) {
        var wrapper = findWrapper(key);
        if (wrapper == null) {
            return null;
        }

        return new CommandRegistryArgumentProxy(event, wrapper);
    }

    @Override
    public Object getMemberKeys() {
        return Arrays.stream(ArgumentTypeWrappers.values()).map(Enum::name).toArray(String[]::new);
    }

    @Override
    public boolean hasMember(String key) {
        return findWrapper(key) != null;
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("Arguments is read-only");
    }

    @Override
    public boolean removeMember(String key) {
        return false;
    }

    private ArgumentTypeWrappers findWrapper(String key) {
        try {
            return ArgumentTypeWrappers.valueOf(key);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static final class CommandRegistryArgumentProxy implements ProxyObject {
        private final CommandRegistryEventJS event;
        private final ArgumentTypeWrappers wrapper;

        private CommandRegistryArgumentProxy(CommandRegistryEventJS event, ArgumentTypeWrappers wrapper) {
            this.event = event;
            this.wrapper = wrapper;
        }

        @Override
        public Object getMember(String key) {
            if ("create".equals(key)) {
                return (ProxyExecutable) arguments -> wrapper.create(event);
            }

            if ("getResult".equals(key)) {
                return (ProxyExecutable) arguments -> {
                    if (arguments.length < 2) {
                        throw new IllegalArgumentException("getResult requires a CommandContext and input name");
                    }

                    var context = toCommandContext(arguments[0]);
                    var input = arguments[1].isNull() ? null : arguments[1].asString();
                    return resolveResult(context, input);
                };
            }

            return null;
        }

        @Override
        public Object getMemberKeys() {
            return new String[]{"create", "getResult"};
        }

        @Override
        public boolean hasMember(String key) {
            return "create".equals(key) || "getResult".equals(key);
        }

        @Override
        public void putMember(String key, Value value) {
            throw new UnsupportedOperationException("Argument wrapper is read-only");
        }

        @Override
        public boolean removeMember(String key) {
            return false;
        }

        @SuppressWarnings("unchecked")
        private CommandContext<net.minecraft.commands.CommandSourceStack> toCommandContext(Value value) {
            if (value.isHostObject()) {
                return (CommandContext<net.minecraft.commands.CommandSourceStack>) value.asHostObject();
            }

            return value.as(CommandContext.class);
        }

        private Object resolveResult(CommandContext<net.minecraft.commands.CommandSourceStack> context, String input) {
            try {
                return wrapper.getResult(context, input);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to resolve argument result for " + wrapper.name(), ex);
            }
        }
    }
}