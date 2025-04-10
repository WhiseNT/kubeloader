package com.whisent.kubeloader.impl.zip;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import com.whisent.kubeloader.files.FileIO;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptSource;
import dev.latvian.mods.kubejs.script.ScriptType;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.EnumMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.zip.ZipFile;

public class ZipContentPack implements ContentPack {
    private final ZipFile zipFile;
    private final Map<ScriptType, ScriptPack> packs = new EnumMap<>(ScriptType.class);
    private final PackMetaData metaData;

    public ZipContentPack(File file) throws IOException {
        this.zipFile = new ZipFile(file);
        this.metaData = loadMetaData();
    }

    @Override
    public PackMetaData getMetaData() {
        return metaData;
    }

    private PackMetaData loadMetaData() {
        var entry = zipFile.getEntry(Kubeloader.META_DATA_FILE_NAME);
        if (entry == null) {
            throw new NoSuchElementException(String.format("No valid %s found", Kubeloader.META_DATA_FILE_NAME));
        } else if (entry.isDirectory()) {
            throw new IllegalArgumentException(String.format(
                "%s should be a file, but got an directory",
                Kubeloader.META_DATA_FILE_NAME
            ));
        }
        JsonObject jsonObject;
        try (var reader = FileIO.stream2reader(zipFile.getInputStream(entry))) {
            jsonObject = Kubeloader.GSON.fromJson(reader, JsonObject.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var result = PackMetaData.CODEC.parse(JsonOps.INSTANCE, jsonObject);
        if (result.result().isPresent()) {
            return result.result().get();
        }
        var errorMessage = result.error().orElseThrow().message();
        throw new RuntimeException("Error when parsing metadata: " + errorMessage);
    }

    @Override
    public @Nullable ScriptPack getPack(PackLoadingContext context) {
        return packs.computeIfAbsent(
            context.type(), k -> {
                try {
                    return createPack(context);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        );
    }

    private ScriptPack createPack(PackLoadingContext context) throws IOException {
        var pack = ContentPack.createEmptyPack(context, id());
        var prefix = context.folderName() + '/';
        zipFile.stream()
            .filter(e -> !e.isDirectory())
            .filter(e -> e.getName().endsWith(".js"))
            .filter(e -> e.getName().startsWith(prefix))
            .forEach(zipEntry -> {
                var zipFileInfo = new ScriptFileInfo(pack.info, zipEntry.getName());
                var scriptSource = (ScriptSource) info -> {
                    var reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(zipEntry)));
                    return reader.lines().toList();
                };
                context.loadFile(pack, zipFileInfo, scriptSource);
            });

        return pack;
    }

    @Override
    public String toString() {
        return "ZipContentPack[namespace=%s]".formatted(metaData.id());
    }
}
