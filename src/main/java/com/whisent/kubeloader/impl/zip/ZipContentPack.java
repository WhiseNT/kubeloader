package com.whisent.kubeloader.impl.zip;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.cpconfig.JsonReader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import dev.latvian.mods.kubejs.core.mixin.common.mod.REITooltipMixin;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptSource;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.JsonIO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.json.Json;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipContentPack implements ContentPack {
    private final ZipFile zipFile;
    private String namespace;
    private Map config;
    private final Map<ScriptType, ScriptPack> packs = new EnumMap<>(ScriptType.class);
    private JsonObject configJson;
    public ZipContentPack(File file) throws IOException {
        this.zipFile = new ZipFile(file);
        configJson = parseConfig();
        this.config = getCustomOrDefaultConfig();

        Kubeloader.LOGGER.debug("得到config文件"+this.config);
        Kubeloader.LOGGER.debug("得到优先级"+this.config.get("priority"));
    }
    @Override
    public Map getConfig() {
        return config;
    }
    //若不存在自定义config则返回内部config
    private Map getCustomOrDefaultConfig() {
        Path customConfigPath = Kubeloader.ConfigPath.resolve(computeNamespace() + ".json");
        if (Files.notExists(customConfigPath)) {
            try {
                JsonObject obj = parseConfig();
                JsonIO.write(customConfigPath,obj);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return getObjectToMap(configJson);
        } else {
            return JsonReader.loadConfig(customConfigPath);
        }
    }

    @Override
    public @NotNull String getNamespace() {
        if (namespace == null) {
            namespace = computeNamespace();
        }
        return namespace;
    }

    private String computeNamespace() {
        Set<String> firstLevelFolders = zipFile.stream()
                .map(ZipEntry::getName)
                .filter(name -> name.endsWith("/"))
                .filter(name -> name.chars().filter(c -> c == '/').count() == 1)
                .collect(Collectors.toSet());
        if (firstLevelFolders.size() != 1) {
            Kubeloader.LOGGER.info("ContentPack根目录内包含多余文件夹或不包含任何文件夹");
            Kubeloader.LOGGER.debug(firstLevelFolders.toString());
            return "";
        } else {
            Kubeloader.LOGGER.debug("获取namespace为{}", firstLevelFolders.iterator().next().split("/")[0]);
            return firstLevelFolders.iterator().next().split("/")[0];
        }
    }

    @Override
    public @Nullable ScriptPack getPack(PackLoadingContext context) {
        return packs.computeIfAbsent(context.type(), k -> {
            try {
                return createPack(context);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private ScriptPack createPack(PackLoadingContext context) throws IOException {
        var pack = createEmptyPack(context);
            var parent = zipFile.getEntry(namespace);
            if (parent == null || !parent.isDirectory()) {
                return null;
            }
        var prefix = namespace + '/' + context.folderName() + '/';
            zipFile.stream()
                    .filter(e -> !e.isDirectory())
                    .filter(e -> e.getName().endsWith(".js"))
                    .filter(e -> e.getName().startsWith(prefix))
                    .forEach(zipEntry -> {
                        var zipFileInfo = new ScriptFileInfo(pack.info,zipEntry.getName());
                        var scriptSource = (ScriptSource) info -> {
                                var reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(zipEntry)));
                            return reader.lines().toList();
                        };
                        context.loadFile(pack,zipFileInfo,scriptSource);
                    });


        return pack;
    }
    private JsonObject parseConfig() {
        final JsonObject[] list = new JsonObject[1];
        //搜索config文件
        zipFile.stream().filter(e -> !e.isDirectory()).filter(e -> e.getName().endsWith("config.json"))
                .forEach(zipEntry -> {
                    BufferedReader reader = null;
                    try {
                        reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(zipEntry)));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    StringBuilder jsonContent = new StringBuilder();
                    String line;
                    while (true) {
                        try {
                            if ((line = reader.readLine()) == null) break;
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        jsonContent.append(line);
                    }

                    JsonObject json = JsonParser.parseString(jsonContent.toString()).getAsJsonObject();
                    list[0] = json;
                });
        return list[0];
    }
    private Map getObjectToMap(JsonObject object) {
        return JsonReader.parseJsonObject(object);
    }

    @Override
    public int getPriority() {
        Object priority = this.config.get("priority");
        return priority == null ? 0 : Integer.parseInt(priority.toString());

    }

    @Override
    public String toString() {
        return "ZipContentPack[namespace=%s]".formatted(getNamespace());
    }

}
