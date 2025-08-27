package com.whisent.kubeloader.impl.path;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.metadata.pack.PackMetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class PathContentPackResources implements PackResources {
    private final String name;
    private final PathContentPack contentPack;
    private PackMetadataSection packMeta;

    public PathContentPackResources(String name, PathContentPack contentPack) {
        this.name = name;
        this.contentPack = contentPack;
    }

    @Override
    public String packId() {
        return name;
    }

    @Override
    public String toString() {
        return "PathContentPackResources[" + this.name + "]";
    }

    @Override
    @Nullable
    public IoSupplier<InputStream> getRootResource(String... strings) {
        // 不支持根资源
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
    public <T> T getMetadataSection(MetadataSectionSerializer<T> metadataSectionSerializer) {
        if (metadataSectionSerializer instanceof PackMetadataSectionSerializer) {
            if (packMeta == null) {
                packMeta = createDefaultPackMeta();
            }
            return (T) packMeta;
        }
        return null;
    }

    private PackMetadataSection createDefaultPackMeta() {
        // 创建默认的pack metadata
        return new PackMetadataSection(
            net.minecraft.network.chat.Component.literal("Content Pack: " + name),
            15 // 默认使用1.20.1的pack format版本
        );
    }

    @Override
    public void close() {
        // 不需要关闭任何资源
    }
}