package com.whisent.kubeloader.impl.zip;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.ContentPackProvider;
import com.whisent.kubeloader.definition.ContentPackUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipFile;

public class ZipContentPackProvider implements ContentPackProvider {
    private final Path basePath;

    public ZipContentPackProvider(Path basePath) {
        this.basePath = basePath;
    }

    @Override
    public @NotNull Collection<? extends @NotNull ContentPack> providePack() {
        try {
            return Files.list(basePath)
                .filter(Files::isRegularFile)
                .map(Path::toFile)
                .filter(f -> f.getName().endsWith(".zip"))
                .map(this::safelyScanSingle)
                .filter(Objects::nonNull)
                .toList();
        } catch (IOException e) {
            Kubeloader.LOGGER.error("Error when scanning zip file for ContentPack, ignoring all zip");
            return List.of();
        }
    }

    private ContentPack safelyScanSingle(File file) {
        try (var zipFile = new ZipFile(file)) {
            var entry = zipFile.getEntry(Kubeloader.META_DATA_FILE_NAME);
            if (entry == null) {
                return null;
            } else if (entry.isDirectory()) {
                throw new RuntimeException(String.format(
                    "%s should be a file, but got a directory",
                    Kubeloader.META_DATA_FILE_NAME
                ));
            }
            var metadata = ContentPackUtils.loadMetaDataOrThrow(zipFile.getInputStream(entry));
            return new ZipContentPack(file.toPath(), metadata);
        } catch (Exception e) {
            Kubeloader.LOGGER.error("Error when scanning zip file: {}", file.getName(), e);
            return null;
        }
    }
}
