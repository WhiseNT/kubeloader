package com.whisent.kubeloader.impl.path;

import com.google.gson.JsonObject;
import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.cpconfig.JsonReader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptSource;
import dev.latvian.mods.kubejs.util.JsonIO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.json.Json;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * @author ZZZank
 */
public class PathContentPack implements ContentPack {
    private final Path base;
    private final String namespace;
    private final Map config;


    public PathContentPack(Path base) {
        this.base = base;
        namespace = base.getFileName().toString();
        this.config = getCustomOrDefaultConfig();
        Kubeloader.LOGGER.debug("寻找到config"+this.config);

    }
    @Override
    public Map getConfig() {
        return config;
    }
    //若不存在自定义config则返回内部config
    private Map getCustomOrDefaultConfig() {
        Path customConfigPath = Kubeloader.ConfigPath.resolve(namespace + ".json");
        if (Files.notExists(customConfigPath)) {
            try {
                JsonObject obj = JsonReader.getJsonObject(base.resolve("config.json"));
                JsonIO.write(customConfigPath,obj);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return JsonReader.loadConfig(base.resolve("config.json"));
        } else {
            return JsonReader.loadConfig(customConfigPath);
        }
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
