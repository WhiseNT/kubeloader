package com.whisent.kubeloader.impl.zip;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptSource;
import dev.latvian.mods.kubejs.script.ScriptType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipContentPack implements ContentPack {
    private final ZipFile zipFile;
    private String namespace;
    private final Map<ScriptType, ScriptPack> packs = new EnumMap<>(ScriptType.class);

    public ZipContentPack(File file) throws IOException {
        this.zipFile = new ZipFile(file);
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
            Kubeloader.LOGGER.debug("获取namespace为"+firstLevelFolders.iterator().next().split("/")[0]);
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
            zipFile.stream()
                    .filter(e -> !e.isDirectory())
                    .filter(e -> e.getName().endsWith(".js"))
                    .filter(e -> e.getName().startsWith(namespace))
                    .forEach(zipEntry -> {
                        var zipFileInfo = new ScriptFileInfo(pack.info,zipEntry.getName());
                        var scriptSource = (ScriptSource) info -> {
                                var reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(zipEntry)));
                            return reader.lines().toList();
                        };
                        context.loadFile(pack,zipFileInfo,scriptSource);
                    });
        return pack;
    };
}
