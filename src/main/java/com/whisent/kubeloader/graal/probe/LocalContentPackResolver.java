package com.whisent.kubeloader.graal.probe;

import com.whisent.kubeloader.Kubeloader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class LocalContentPackResolver {
    private LocalContentPackResolver() {
    }

    public static List<String> listLocalPackIds() {
        Path packRoot = Kubeloader.PackPath;
        if (packRoot == null || !Files.isDirectory(packRoot)) {
            return List.of();
        }

        try (var stream = Files.walk(packRoot)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> Kubeloader.META_DATA_FILE_NAME.equals(path.getFileName().toString()))
                    .map(Path::getParent)
                    .map(path -> normalizePackId(packRoot.relativize(path)))
                    .sorted()
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    public static Resolution resolve(String packId) {
        if (packId == null || packId.isBlank()) {
            return Resolution.failure("Pack id is required");
        }

        Path rootPath = Kubeloader.PackPath.resolve(packId).normalize();
        if (!rootPath.startsWith(Kubeloader.PackPath.normalize())) {
            return Resolution.failure("Invalid local pack id: " + packId);
        }

        if (!Files.isDirectory(rootPath)) {
            return Resolution.failure("Local content pack not found: " + packId);
        }

        if (!Files.isRegularFile(rootPath.resolve(Kubeloader.META_DATA_FILE_NAME))) {
            return Resolution.failure("Missing contentpack.json for pack: " + packId);
        }

        return Resolution.success(new ResolvedPack(packId, rootPath));
    }

    private static String normalizePackId(Path relativePath) {
        return relativePath.toString().replace('\\', '/');
    }

    public record ResolvedPack(String packId, Path rootPath) {
    }

    public record Resolution(boolean success, String message, ResolvedPack pack) {
        public static Resolution success(ResolvedPack pack) {
            return new Resolution(true, "", pack);
        }

        public static Resolution failure(String message) {
            return new Resolution(false, message, null);
        }
    }
}