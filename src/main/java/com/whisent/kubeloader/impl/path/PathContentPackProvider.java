package com.whisent.kubeloader.impl.path;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.ContentPackProvider;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * @author ZZZank
 */
public class PathContentPackProvider implements ContentPackProvider {
    private final Path base;

    public PathContentPackProvider(Path base) {
        this.base = base;
    }

    @Override
    public @NotNull Collection<? extends @NotNull ContentPack> providePack() {
        try {
            return Files.list(base)
                .filter(Files::isDirectory)
                .map(this::safelyScanSingle)
                .filter(Objects::nonNull)
                .toList();
        } catch (IOException e) {
            Kubeloader.LOGGER.error("Error when collecting ContentPack information from path", e);
            return List.of();
        }
    }

    private ContentPack safelyScanSingle(Path path) {
        try {
            return new PathContentPack(path);
        } catch (Exception e) {
            Kubeloader.LOGGER.error("Error when scanning path: {}", path.getFileName(), e);
            return null;
        }
    }
}