package com.whisent.kubeloader.impl.zip;

import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.definition.meta.PackMetaData;
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
import java.util.zip.ZipFile;

public class ZipContentPack implements ContentPack {
    private final ZipFile zipFile;
    private final Map<ScriptType, ScriptPack> packs = new EnumMap<>(ScriptType.class);
    private final PackMetaData metaData;

    public ZipContentPack(File file, PackMetaData metaData) throws IOException {
        this.zipFile = new ZipFile(file);
        this.metaData = metaData;
    }

    @Override
    public PackMetaData getMetaData() {
        return metaData;
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
