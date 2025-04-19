package com.whisent.kubeloader.impl.dummy;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.server.packs.repository.Pack;
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
    private final PackMetaData metaData;

    public DummyContentPack(String namespace, Function<PackLoadingContext, ScriptPack> toPack) {
        this.namespace = namespace;
        this.toPack = toPack;
        this.metaData = createMetaData();
    }

    @Override
    public PackMetaData getMetaData() {
        return metaData;
    }
    public PackMetaData createMetaData() {
        JsonObject json = new JsonObject();
        json.addProperty("id", "kubejs");
        json.addProperty("version", "1.0.0");
        var result = PackMetaData.CODEC.parse(
                JsonOps.INSTANCE,
                Kubeloader.GSON.fromJson(json, JsonObject.class)
        );
        if (result.result().isPresent()) {
            return result.result().get();
        } else {
            return ContentPack.super.getMetaData();
        }
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
