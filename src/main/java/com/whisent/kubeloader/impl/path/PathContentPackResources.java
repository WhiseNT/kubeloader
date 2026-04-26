package com.whisent.kubeloader.impl.path;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class PathContentPackResources implements PackResources {
    private final PackLocationInfo location;
    private final PathContentPack contentPack;

    public PathContentPackResources(PackLocationInfo location, PathContentPack contentPack) {
        this.location = location;
        this.contentPack = contentPack;
    }

    @Override
    public String packId() {
        return location.id();
    }

    @Override
    public String toString() {
        return "PathContentPackResources[" + this.location.id() + "]";
    }

    @Override
    @Nullable
    public IoSupplier<InputStream> getRootResource(String... strings) {
        if (strings.length == 1 && "pack.mcmeta".equals(strings[0])) {
            var meta = "{" +
                    "\"pack\":{" +
                    "\"description\":\"Content Pack: " + location.id() + "\"," +
                    "\"pack_format\":" + SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA) +
                    "}}";
            var bytes = meta.getBytes(StandardCharsets.UTF_8);
            return () -> new ByteArrayInputStream(bytes);
        }
        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation resourceLocation) {
        return contentPack.getResource(packType, resourceLocation);
    }

    @Override
    public void listResources(PackType packType, String namespace, String path, ResourceOutput resourceOutput) {
        Path basePath = packType == PackType.CLIENT_RESOURCES ? 
            contentPack.getResourcePath() : 
            contentPack.getDataPath();
            
        Path namespacePath = basePath.resolve(namespace).resolve(path);
        
        if (Files.isDirectory(namespacePath)) {
            try (var paths = Files.walk(namespacePath)) {
                paths.filter(Files::isRegularFile)
                     .forEach(file -> {
                         String fileName = namespacePath.relativize(file).toString().replace('\\', '/');
                         String fullPath = path.isEmpty() ? fileName : path + "/" + fileName;
                         ResourceLocation location = ResourceLocation.tryBuild(namespace, fullPath);
                         if (location != null) {
                             resourceOutput.accept(location, IoSupplier.create(file));
                         }
                     });
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType packType) {
        return contentPack.getNamespaces(packType);
    }

    @Override
    @Nullable
    public <T> T getMetadataSection(MetadataSectionSerializer<T> metadataSectionSerializer) throws IOException {
        var inputSupplier = getRootResource("pack.mcmeta");

        if (inputSupplier != null) {
            try (var input = inputSupplier.get()) {
                return AbstractPackResources.getMetadataFromStream(metadataSectionSerializer, input);
            }
        }

        return null;
    }

    @Override
    public void close() {
        // 不需要关闭任何资源
    }

    @Override
    public PackLocationInfo location() {
        return location;
    }
}