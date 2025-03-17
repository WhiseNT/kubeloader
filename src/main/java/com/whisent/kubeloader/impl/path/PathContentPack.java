package com.whisent.kubeloader.impl.path;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.cpconfig.JsonReader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * @author ZZZank
 */
public class PathContentPack implements ContentPack {
    private final Path base;
    private final String namespace;
    private final ArrayList config;

    public PathContentPack(Path base) {
        this.base = base;
        namespace = base.getFileName().toString();
        this.config = JsonReader.loadContentPackConfig(base.resolve("config.json"));
        Kubeloader.LOGGER.debug("寻找到config"+this.config);
    }

    @Override
    public @NotNull String getNamespace() {
        return namespace;
    }

    @Override
    public @Nullable ScriptPack getPack(PackLoadingContext context) {
        var scriptPath = base.resolve(context.folderName());
        if (!Files.isDirectory(scriptPath)) {
            return null;
        }
        var pack = createEmptyPack(context);
        KubeJS.loadScripts(pack, scriptPath, "");
        for (var fileInfo : pack.info.scripts) {
            var scriptSource = (ScriptSource.FromPath) (info) -> scriptPath.resolve(info.file);
            context.loadFile(pack, fileInfo, scriptSource);
        }
        return pack;
    }

    @Override
    public String toString() {
        return "PathContentPack[namespace='%s']".formatted(namespace);
    }
}
