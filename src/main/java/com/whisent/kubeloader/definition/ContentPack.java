package com.whisent.kubeloader.definition;

import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import com.whisent.kubeloader.files.FileIO;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptPackInfo;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

/**
 * 一个 ContentPack 是 KubeJS 脚本（与资源）的集合，提供命名空间（{@link dev.latvian.mods.kubejs.script.ScriptPackInfo#namespace}
 * ）独立的脚本执行环境以及命名空间（{@link ResourceLocation#getNamespace()}）独立的资源集合
 *
 * @author ZZZank
 */
public interface ContentPack {

    PackMetaData getMetaData();

    /**
     * 如果该 ContentPack 没有{@link PackLoadingContext#type()} 对应的 {@link ScriptPack}，返回 {@code null}
     */
    @Nullable
    ScriptPack getPack(PackLoadingContext context);

    @NotNull
    default String id() {
        return this.getMetaData().id();
    }

    @NotNull
    default ScriptPack postProcessPack(PackLoadingContext context, @NotNull ScriptPack pack) {
        pack.scripts.sort(null);
        return pack;
    }

    static ScriptPack createEmptyPack(PackLoadingContext context, String id) {
        return new ScriptPack(context.manager(), new ScriptPackInfo(id, ""));
    }

    static DataResult<PackMetaData> loadMetaData(InputStream stream) {
        try (var reader = FileIO.stream2reader(stream)) {
            var json = Kubeloader.GSON.fromJson(reader, JsonObject.class);
            return PackMetaData.CODEC.parse(JsonOps.INSTANCE, json);
        } catch (IOException e) {
            return DataResult.error(e::toString);
        }
    }

    static PackMetaData loadMetaDataOrThrow(InputStream stream) {
        var result = loadMetaData(stream);
        if (result.error().isPresent()) {
            throw new RuntimeException(result.error().get().message());
        }
        return result.result().orElseThrow();
    }
}
