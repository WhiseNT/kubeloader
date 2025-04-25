package com.whisent.kubeloader.impl.path;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.ContentPackUtils;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptSource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author ZZZank
 */
public class PathContentPack implements ContentPack {
    private final Path base;
    private final PackMetaData metaData;

    public PathContentPack(Path base) {
        this.base = base;
        this.metaData = loadMetaData(base);
    }

    private PackMetaData loadMetaData(Path base) {
        try (var reader = Files.newBufferedReader(base.resolve(Kubeloader.META_DATA_FILE_NAME))) {
            var result = PackMetaData.CODEC.parse(
                JsonOps.INSTANCE,
                Kubeloader.GSON.fromJson(reader, JsonObject.class)
            );
            if (result.result().isPresent()) {
                return result.result().get();
            }
            var errorMessage = result.error().orElseThrow().message();
            throw new RuntimeException("Error when parsing metadata: " + errorMessage);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public PackMetaData getMetaData() {
        return metaData;
    }

    @Override
    public @Nullable ScriptPack getPack(PackLoadingContext context) {
        var scriptPath = base.resolve(context.folderName());
        if (!Files.isDirectory(scriptPath)) {
            return null;
        }
        var pack = ContentPackUtils.createEmptyPack(context, id());
        KubeJS.loadScripts(pack, scriptPath, "");
        for (var fileInfo : pack.info.scripts) {
            var scriptSource = (ScriptSource.FromPath) (info) -> scriptPath.resolve(info.file);
            context.loadFile(pack, fileInfo, scriptSource);
        }
        return pack;
    }

    @Override
    public String toString() {
        return "PathContentPack[namespace='%s']".formatted(id());
    }
}
