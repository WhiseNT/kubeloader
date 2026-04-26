package com.whisent.kubeloader.impl.zip;

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
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
@Deprecated
public class ZipContentPackResources implements PackResources {
    private final PackLocationInfo location;
    private final ZipContentPack contentPack;
    private final Path path;
    private final String resourcePrefix;
    private final String dataPrefix;

    public ZipContentPackResources(PackLocationInfo location, ZipContentPack contentPack) {
        this.location = location;
        this.contentPack = contentPack;
        this.path = contentPack.getPath();
        this.resourcePrefix = "pack_resources/assets/";
        this.dataPrefix = "pack_resources/data/";
    }

    @Override
    public String packId() {
        return location.id();
    }

    @Override
    public String toString() {
        return "ZipContentPackResources[" + this.location.id() + "]";
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