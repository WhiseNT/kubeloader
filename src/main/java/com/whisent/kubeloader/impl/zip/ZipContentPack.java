package com.whisent.kubeloader.impl.zip;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import dev.latvian.mods.kubejs.script.ScriptPack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipContentPack implements ContentPack {
    private final ZipFile zipFile;
    private final String namespace;
    public ZipContentPack(File file) throws IOException {
        this.zipFile = new ZipFile(file);
        this.namespace = getNamespace();
    }

    @Override
    public @NotNull String getNamespace() {
        Set<String> firstLevelFolders = zipFile.stream()
                .map(ZipEntry::getName)
                .filter(name -> name.contains("/") || name.endsWith("/"))
                .map(name -> {
                    if (name.endsWith("/")) {
                        return name.substring(0, name.length() - 1);
                    } else {
                        return name.substring(0, name.indexOf('/'));
                    }
                })
                .collect(Collectors.toSet());
        if (firstLevelFolders.size() != 1) {
            Kubeloader.LOGGER.info("ContentPack根目录内包含多余文件夹或不包含任何文件夹");
            return "";
        } else {
            return firstLevelFolders.iterator().next();
        }
    }

    @Override
    public @Nullable ScriptPack getPack(PackLoadingContext context) {
        return null;
    }

    private ScriptPack createPack(PackLoadingContext context) {
        var pack = createEmptyPack(context);
        return null;
    };
}
