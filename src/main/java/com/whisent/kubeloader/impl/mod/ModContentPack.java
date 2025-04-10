package com.whisent.kubeloader.impl.mod;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import com.whisent.kubeloader.files.FileIO;
import dev.latvian.mods.kubejs.script.*;
import net.minecraftforge.forgespi.language.IModInfo;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
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
        this.metaData = loadMetaData();
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

    private PackMetaData loadMetaData() {
        var jsonObject = searchMetaData();
        if (jsonObject == null) {
            // or build on from mod itself?
            throw new IllegalStateException("no metadata file found in mod file");
        }
        var result = PackMetaData.CODEC.parse(
            JsonOps.INSTANCE,
            Kubeloader.GSON.fromJson(jsonObject, JsonObject.class)
        );
        if (result.result().isPresent()) {
            return result.result().get();
        }
        var errorMessage = result.error().orElseThrow().message();
        throw new RuntimeException("Error when parsing metadata: " + errorMessage);
    }

    private JsonObject searchMetaData() {
        try (var file = new JarFile(mod.getOwningFile().getFile().getFilePath().toFile())) {
            // TODO: it's a bad idea to scan through all files, we need an explicit path
            return file.stream()
                .filter(e -> !e.isDirectory())
                .filter(e -> e.getName().endsWith(Kubeloader.META_DATA_FILE_NAME))
                .map(entry -> {
                    try (var reader = FileIO.stream2reader(file.getInputStream(entry))) {
                        return Kubeloader.GSON.fromJson(reader, JsonObject.class);
                    } catch (IOException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "ModContentPack[mod=%s]".formatted(mod.getModId());
    }
}
