package com.whisent.kubeloader.mixin.graal;

import dev.latvian.mods.kubejs.recipe.NamespaceFunction;
import dev.latvian.mods.kubejs.recipe.schema.RecipeNamespace;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(value = NamespaceFunction.class, remap = false)
public abstract class NamespaceFunctionMixin implements ProxyObject, ProxyExecutable {
    @Shadow
    private Map<String, ?> map;

    @Shadow
    private RecipeNamespace namespace;

    @Override
    public Object execute(Value... arguments) {
        return namespace.keySet();
    }

    @Override
    public Object getMember(String key) {
        return map.get(key);
    }

    @Override
    public Object getMemberKeys() {
        return map.keySet().toArray(String[]::new);
    }

    @Override
    public boolean hasMember(String key) {
        return map.containsKey(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("NamespaceFunction is read-only");
    }
}