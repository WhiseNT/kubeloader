package com.whisent.kubeloader.impl.dummy;

import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

/**
 * @author ZZZank
 */
public class DummyContentPack implements ContentPack {
    private final String namespace;
    private final Function<PackLoadingContext, ScriptPack> toPack;

    public DummyContentPack(String namespace, Function<PackLoadingContext, ScriptPack> toPack) {
        this.namespace = namespace;
        this.toPack = toPack;
    }

    public DummyContentPack(String namespace, Map<ScriptType, ScriptPack> toPack) {
        this(namespace, cx -> toPack.get(cx.type()));
    }

    @Override
    public @NotNull String getNamespace() {
        return namespace;
    }

    @Override
    public @Nullable ScriptPack getPack(PackLoadingContext context) {
        return toPack.apply(context);
    }
}
