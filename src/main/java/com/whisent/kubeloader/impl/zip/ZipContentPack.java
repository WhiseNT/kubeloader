package com.whisent.kubeloader.impl.zip;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import com.whisent.kubeloader.impl.ContentPackBase;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptSource;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.zip.ZipFile;

public class ZipContentPack extends ContentPackBase {
    private final Path path;

    public ZipContentPack(Path path, PackMetaData metaData) {
        super(metaData);
        this.path = path;
    }

    @Override
    @Nullable
    protected ScriptPack createPack(PackLoadingContext context) {
        var pack = ContentPack.createEmptyPack(context, id());
        var prefix = context.folderName() + '/';
        try (var zipFile = new ZipFile(path.toFile())) {
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
        } catch (IOException e) {
            // TODO: log
            return null;
        }
    }

    @Override
    public String toString() {
        return "ZipContentPack[namespace=%s]".formatted(metaData.id());
    }
}
