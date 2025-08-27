package com.whisent.kubeloader.impl.zip;

import com.whisent.kubeloader.definition.ContentPackUtils;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import com.whisent.kubeloader.impl.ContentPackBase;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipContentPack extends ContentPackBase {
    private final Path path;
    private final String resourcePrefix;
    private final String dataPrefix;

    public ZipContentPack(Path path, PackMetaData metaData) {
        super(metaData);
        this.path = path;
        this.resourcePrefix = "pack_resources/assets/";
        this.dataPrefix = "pack_resources/data/";
    }
    
    public Path getPath() {
        return path;
    }

    @Override
    @Nullable
    protected ScriptPack createPack(PackLoadingContext context) {
        var pack = ContentPackUtils.createEmptyPack(context, id());
        var prefix = context.folderName() + '/';
        try (var zipFile = new ZipFile(path.toFile())) {
            zipFile.stream()
                .filter(e -> !e.isDirectory())
                .filter(e -> e.getName().endsWith(".js"))
                .filter(e -> e.getName().startsWith(prefix))
                .forEach(zipEntry -> {
                    var zipFileInfo = new ScriptFileInfo(pack.info, zipEntry.getName());
                    var scriptSource = (ScriptSource) info -> {
                                var reader = new BufferedReader(new InputStreamReader(
                                        zipFile.getInputStream(zipEntry), StandardCharsets.UTF_8));
                        return reader.lines().toList();
                    };
                    context.loadFile(pack, zipFileInfo, scriptSource);
                });
            loadCommonScripts(pack, context);
            return pack;
        } catch (IOException e) {
            // TODO: log
            return null;
        }
    }

    @Override
    public void loadCommonScripts(ScriptPack pack, PackLoadingContext context) {
        var commonPack = ContentPackUtils.createEmptyPack(context, id());
        var prefix = "common_scripts/";
        try (var zipFile = new ZipFile(path.toFile())) {
            zipFile.stream()
                .filter(e -> !e.isDirectory())
                .filter(e -> e.getName().endsWith(".js"))
                .filter(e -> e.getName().startsWith(prefix))
                .forEach(zipEntry -> {
                    var zipFileInfo = new ScriptFileInfo(commonPack.info, zipEntry.getName());
                    var scriptSource = (ScriptSource) info -> {
                        var reader = new BufferedReader(new InputStreamReader(
                                zipFile.getInputStream(zipEntry), StandardCharsets.UTF_8));
                        return reader.lines().toList();
                    };
                    context.loadFile(pack, zipFileInfo, scriptSource);
                });
            pack.info.scripts.addAll(commonPack.info.scripts);
        } catch (IOException e) {
            // TODO: log
        }
    }
    
    public Set<String> getNamespaces(PackType packType) {
        String prefix = packType == PackType.CLIENT_RESOURCES ? resourcePrefix : dataPrefix;
        Set<String> namespaces = new HashSet<>();

        try (ZipFile zipFile = new ZipFile(path.toFile())) {
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

    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
        String prefix = packType == PackType.CLIENT_RESOURCES ? resourcePrefix : dataPrefix;
        String path = prefix + location.getNamespace() + "/" + location.getPath();

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

    private String getNamespaceFromPath(String path, String prefix) {
        String relativePath = path.substring(prefix.length());
        int firstSlash = relativePath.indexOf('/');
        if (firstSlash != -1) {
            return relativePath.substring(0, firstSlash);
        }
        return "";
    }

    @Override
    public String toString() {
        return "ZipContentPack[namespace=%s]".formatted(metaData.id());
    }
}