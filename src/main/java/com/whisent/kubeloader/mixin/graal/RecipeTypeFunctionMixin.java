package com.whisent.kubeloader.mixin.graal;

import dev.latvian.mods.kubejs.recipe.RecipeTypeFunction;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mixin(value = RecipeTypeFunction.class, remap = false)
public abstract class RecipeTypeFunctionMixin implements ProxyExecutable {
    @Override
    public Object execute(Value... arguments) {
        var converted = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            converted[i] = toJavaValue(arguments[i]);
        }
        return ((RecipeTypeFunction) (Object) this).createRecipe(converted);
    }

    private static Object toJavaValue(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }

        if (value.isHostObject()) {
            return value.asHostObject();
        }

        if (value.isString()) {
            return value.asString();
        }

        if (value.isBoolean()) {
            return value.asBoolean();
        }

        if (value.isNumber()) {
            if (value.fitsInInt()) return value.asInt();
            if (value.fitsInLong()) return value.asLong();
            if (value.fitsInDouble()) return value.asDouble();
            return value.as(Number.class);
        }

        if (value.hasArrayElements()) {
            long size = value.getArraySize();
            List<Object> list = new ArrayList<>((int) size);
            for (long i = 0; i < size; i++) {
                list.add(toJavaValue(value.getArrayElement(i)));
            }
            return list;
        }

        if (value.hasMembers()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : value.getMemberKeys()) {
                map.put(key, toJavaValue(value.getMember(key)));
            }
            return map;
        }

        return value.toString();
    }
}