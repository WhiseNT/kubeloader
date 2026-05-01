package com.whisent.kubeloader.graal.command;

import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Arrays;

public final class BuiltinSuggestionsProxy implements ProxyObject {
    @Override
    public Object getMember(String key) {
        if ("suggest".equals(key)) {
            return (ProxyExecutable) arguments -> {
                if (arguments.length != 2) {
                    throw new IllegalArgumentException("builtinSuggestions.suggest requires exactly 2 arguments");
                }

                var values = arguments[0].isHostObject() ? arguments[0].asHostObject() : arguments[0].as(Object.class);
                var builder = toSuggestionsBuilder(arguments[1]);
                return SharedSuggestionProvider.suggest((Iterable<String>) values, builder);
            };
        }

        return null;
    }

    @Override
    public Object getMemberKeys() {
        return new String[]{"suggest"};
    }

    @Override
    public boolean hasMember(String key) {
        return "suggest".equals(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("builtinSuggestions proxy is read-only");
    }

    @Override
    public boolean removeMember(String key) {
        return false;
    }

    @SuppressWarnings("unchecked")
    private static SuggestionsBuilder toSuggestionsBuilder(Value value) {
        if (value.isHostObject()) {
            return (SuggestionsBuilder) value.asHostObject();
        }

        return value.as(SuggestionsBuilder.class);
    }
}