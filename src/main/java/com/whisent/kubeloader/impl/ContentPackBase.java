package com.whisent.kubeloader.impl;

import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptType;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author ZZZank
 */
public abstract class ContentPackBase implements ContentPack {
    protected final Map<ScriptType, Optional<ScriptPack>> packs = new EnumMap<>(ScriptType.class);
    protected final PackMetaData metaData;

    protected ContentPackBase(PackMetaData metaData) {
        this.metaData = metaData;
    }

    @Nullable
    protected abstract ScriptPack createPack(PackLoadingContext context);

    @Override
    public @Nullable ScriptPack getPack(PackLoadingContext context) {
        return this.packs.computeIfAbsent(
            context.type(),
            t -> Optional.ofNullable(createPack(context))
        ).orElse(null);
    }

    @Override
    public PackMetaData getMetaData() {
        return metaData;
    }
}
