package com.whisent.kubeloader.impl.dummy;

import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import dev.latvian.mods.kubejs.script.ScriptPack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * @author ZZZank
 */
public class DummyContentPack implements ContentPack {
    private final PackMetaData metaData;
    private final Function<PackLoadingContext, ScriptPack> toPack;

    public DummyContentPack(String namespace, Function<PackLoadingContext, ScriptPack> toPack) {
        this(PackMetaData.minimal(namespace), toPack);
    }

    public DummyContentPack(PackMetaData metaData, Function<PackLoadingContext, ScriptPack> toPack) {
        this.metaData = metaData;
        this.toPack = toPack;
    }

    @Override
    public @Nullable ScriptPack getPack(PackLoadingContext context) {
        return toPack.apply(context);
    }

    @Override
    public PackMetaData getMetaData() {
        return metaData;
    }
}
