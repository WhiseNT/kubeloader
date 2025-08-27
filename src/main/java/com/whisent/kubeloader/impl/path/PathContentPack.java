package com.whisent.kubeloader.impl.path;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.ContentPackUtils;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * @author ZZZank
 */
public class PathContentPack implements ContentPack {
    private final Path base;
    private final PackMetaData metaData;
    private final Path resourcePath;
    private final Path dataPath;

    public PathContentPack(Path base) {
        this.base = base;
        this.metaData = loadMetaData(base);
        this.resourcePath = base.resolve("assets");
        this.dataPath = base.resolve("data");
    }

    public Path getResourcePath() {
        return resourcePath;
    }

    public Path getDataPath() {
        return dataPath;
    }

    private PackMetaData loadMetaData(Path base) {
        try (var reader = Files.newBufferedReader(base.resolve(Kubeloader.META_DATA_FILE_NAME))) {
            var result = PackMetaData.CODEC.parse(
                JsonOps.INSTANCE,
                Kubeloader.GSON.fromJson(reader, JsonObject.class)
            );
            if (result.result().isPresent()) {
                return result.result().get();
            }
            var errorMessage = result.error().orElseThrow().message();
            throw new RuntimeException("Error when parsing metadata: " + errorMessage);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public PackMetaData getMetaData() {
        return metaData;
    }

    @Override
    public @Nullable ScriptPack getPack(PackLoadingContext context) {
        var scriptPath = base.resolve(context.folderName());
        if (!Files.isDirectory(scriptPath)) {
            return null;
        }
        var pack = ContentPackUtils.createEmptyPack(context, id());

        KubeJS.loadScripts(pack, scriptPath, "");

        for (var fileInfo : pack.info.scripts) {
            var scriptSource = (ScriptSource.FromPath) (info) -> scriptPath.resolve(info.file);
            context.loadFile(pack, fileInfo, scriptSource);
        }
        loadCommonScripts(pack, context);
        return pack;
    }

    @Override
    public void loadCommonScripts(ScriptPack pack, PackLoadingContext context) {
        var commonPack = ContentPackUtils.createEmptyPack(context, id());
        var sciptPath = base.resolve("common_scripts");
        KubeJS.loadScripts(commonPack, sciptPath, "");
        for (var fileInfo : commonPack.info.scripts) {
            var scriptSource = (ScriptSource.FromPath) (info) -> sciptPath.resolve(info.file);
            context.loadFile(pack, fileInfo, scriptSource);
        }
        pack.info.scripts.addAll(commonPack.info.scripts);
    }

    public Set<String> getNamespaces(PackType packType) {
        Path path = getPathForType(packType);
        Set<String> namespaces = new HashSet<>();

        if (Files.isDirectory(path)) {
            try (var paths = Files.list(path)) {
                paths.filter(Files::isDirectory)
                     .map(p -> p.getFileName().toString())
                     .forEach(namespaces::add);
            } catch (IOException e) {
                Kubeloader.LOGGER.error("Failed to list namespaces in path: {}", path, e);
            }
        }

        return namespaces;
    }

    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
        Path path = getPathForType(packType);
        Path resourcePath = path.resolve(location.getNamespace()).resolve(location.getPath());
        
        if (Files.exists(resourcePath)) {
            return IoSupplier.create(resourcePath);
        }
        
        return null;
    }

    private Path getPathForType(PackType packType) {
        return packType == PackType.CLIENT_RESOURCES ? resourcePath : dataPath;
    }

    @Override
    public String toString() {
        return "PathContentPack[namespace='%s']".formatted(id());
    }
}