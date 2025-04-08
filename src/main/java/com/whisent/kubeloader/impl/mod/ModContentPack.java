package com.whisent.kubeloader.impl.mod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import dev.latvian.mods.kubejs.script.*;
import net.minecraftforge.forgespi.language.IModInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.EnumMap;
import java.util.Map;
import java.util.jar.JarFile;

/**
 * @author ZZZank
 */
public class ModContentPack implements ContentPack {
    private final IModInfo mod;
    private final Map<ScriptType, ScriptPack> packs = new EnumMap<>(ScriptType.class);
    private final PackMetaData metaData;

    public ModContentPack(IModInfo mod) {
        this.mod = mod;
        this.metaData = parseMetaData();
    }

    @Override
    public @NotNull String getNamespace() {
        return mod.getModId();
    }

    @Override
    @Nullable
    public ScriptPack getPack(PackLoadingContext context) {
        return packs.computeIfAbsent(context.type(), k -> createPack(context));
    }

    private ScriptPack createPack(PackLoadingContext context) {
        var pack = createEmptyPack(context);

        var prefix = Kubeloader.FOLDER_NAME + '/' + context.folderName() + '/';
        try (var file = new JarFile(mod.getOwningFile().getFile().getFilePath().toFile())) {
            var parent = file.getEntry(Kubeloader.FOLDER_NAME + '/' + context.folderName());
            if (parent == null || !parent.isDirectory()) {
                return null;
            }
            file.stream()
                .filter(e -> !e.isDirectory())
                .filter(e -> e.getName().endsWith(".js"))
                .filter(e -> e.getName().startsWith(prefix))
                .forEach(jarEntry -> {
                    var fileInfo = new ScriptFileInfo(pack.info, jarEntry.getName());
                    var scriptSource = (ScriptSource) info -> {
                        var reader = new BufferedReader(new InputStreamReader(file.getInputStream(jarEntry)));
                        return reader.lines().toList();
                    };
                    context.loadFile(pack, fileInfo, scriptSource);
                });
        } catch (IOException e) {
            return null;
        }
        return pack;
    }

    @Override
    public PackMetaData getMetaData() {
        return metaData;
    }

    private PackMetaData parseMetaData() {
        JsonObject jsonObject = searchMetaData();
        if (jsonObject != null) {
            var result = PackMetaData.CODEC.parse(
                    JsonOps.INSTANCE,
                    Kubeloader.GSON.fromJson(jsonObject, JsonObject.class)
            );
            if (result.result().isPresent()) {
                return result.result().get();
            } else {
                return ContentPack.super.getMetaData();
            }
        } else {
            return ContentPack.super.getMetaData();
        }
    }
    private JsonObject searchMetaData() {
        final JsonObject[] list = new JsonObject[1];
        //搜索config文件
        try (var file = new JarFile(mod.getOwningFile().getFile().getFilePath().toFile())) {
            file.stream()
                    .filter(e -> !e.isDirectory())
                    .filter(e -> e.getName().endsWith(Kubeloader.META_DATA_FILE_NAME))
                    .forEach(jarEntry -> {
                        BufferedReader reader = null;
                        try {
                            reader = new BufferedReader(new InputStreamReader(file.getInputStream(jarEntry)));
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
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "ModContentPack[mod=%s]".formatted(mod.getModId());
    }
}
