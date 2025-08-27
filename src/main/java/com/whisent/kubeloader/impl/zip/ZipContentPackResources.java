package com.whisent.kubeloader.impl.zip;

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
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipContentPackResources implements PackResources {
    private final String name;
    private final ZipContentPack contentPack;
    private final Path path;
    private final String resourcePrefix;
    private final String dataPrefix;
    private PackMetadataSection packMeta;

    public ZipContentPackResources(String name, ZipContentPack contentPack) {
        this.name = name;
        this.contentPack = contentPack;
        this.path = contentPack.getPath();
        this.resourcePrefix = "pack_resources/assets/";
        this.dataPrefix = "pack_resources/data/";
    }

    @Override
    public String packId() {
        return name;
    }

    @Override
    public String toString() {
        return "ZipContentPackResources[" + this.name + "]";
    }

    @Override
    @Nullable
    public IoSupplier<InputStream> getRootResource(String... strings) {
        // 不支持根资源
        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation resourceLocation) {
        String prefix = packType == PackType.CLIENT_RESOURCES ? resourcePrefix : dataPrefix;
        String path = prefix + resourceLocation.getNamespace() + "/" + resourceLocation.getPath();

        try (ZipFile zipFile = new ZipFile(this.path.toFile())) {
            ZipEntry entry = zipFile.getEntry(path);
            if (entry != null) {
                return () -> zipFile.getInputStream(entry);
            }
        } catch (IOException e) {
            // Ignore
        }

        return null;
    }

    @Override
    public void listResources(PackType packType, String namespace, String path, ResourceOutput resourceOutput) {
        String prefix = packType == PackType.CLIENT_RESOURCES ? resourcePrefix : dataPrefix;
        String searchPath = prefix + namespace + "/" + path + "/";

        try (ZipFile zipFile = new ZipFile(this.path.toFile())) {
            zipFile.stream()
                .filter(entry -> !entry.isDirectory())
                .filter(entry -> entry.getName().startsWith(searchPath))
                .forEach(entry -> {
                    String fileName = entry.getName().substring(prefix.length() + namespace.length() + 1);
                    String fullPath = path.isEmpty() ? fileName : path + "/" + fileName;
                    ResourceLocation location = ResourceLocation.tryBuild(namespace, fullPath);
                    if (location != null) {
                        resourceOutput.accept(location, () -> zipFile.getInputStream(entry));
                    }
                });
        } catch (IOException e) {
            // Ignore
        }
    }

    @Override
    public Set<String> getNamespaces(PackType packType) {
        String prefix = packType == PackType.CLIENT_RESOURCES ? resourcePrefix : dataPrefix;
        Set<String> namespaces = new HashSet<>();

        try (ZipFile zipFile = new ZipFile(this.path.toFile())) {
            zipFile.stream()
                .filter(entry -> !entry.isDirectory())
                .filter(entry -> entry.getName().startsWith(prefix))
                .map(entry -> getNamespaceFromPath(entry.getName(), prefix))
                .forEach(namespaces::add);
        } catch (IOException e) {
            // Ignore
        }

        return namespaces;
    }

    private String getNamespaceFromPath(String path, String prefix) {
        String relativePath = path.substring(prefix.length());
        int firstSlash = relativePath.indexOf('/');
        if (firstSlash != -1) {
            return relativePath.substring(0, firstSlash);
        }
        return "";
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