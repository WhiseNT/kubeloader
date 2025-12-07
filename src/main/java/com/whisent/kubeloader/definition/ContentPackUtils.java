package com.whisent.kubeloader.definition;

import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import com.whisent.kubeloader.definition.meta.dependency.DependencySource;
import com.whisent.kubeloader.definition.meta.dependency.DependencyType;
import com.whisent.kubeloader.definition.meta.dependency.PackDependency;
import com.whisent.kubeloader.files.FileIO;
import com.whisent.kubeloader.impl.depends.ImmutableDependency;
import com.whisent.kubeloader.impl.depends.ImmutableMetaData;
import dev.latvian.mods.kubejs.KubeJSPaths;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptPackInfo;
import net.minecraftforge.forgespi.language.IModInfo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.*;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;

/**
 * @author ZZZank
 */
public class ContentPackUtils {
    public static ScriptPack createEmptyPack(PackLoadingContext context, String id) {
        return new ScriptPack(context.manager(), new ScriptPackInfo(id, ""));
    }


    public static DataResult<PackMetaData> loadMetaData(InputStream stream) {
        try (var reader = FileIO.stream2reader(stream)) {
            var json = Kubeloader.GSON.fromJson(reader, JsonObject.class);
            return PackMetaData.CODEC.parse(JsonOps.INSTANCE, json);
        } catch (IOException e) {
            return DataResult.error(e::toString);
        }
    }
    public static DataResult<PackMetaData> loadMetaData(Path path) {
        try (var reader = FileIO.file2reader(path.toFile())) {
            var json = Kubeloader.GSON.fromJson(reader, JsonObject.class);
            return PackMetaData.CODEC.parse(JsonOps.INSTANCE, json);
        } catch (IOException e) {
            return DataResult.error(e::toString);
        }
    }

    public static PackMetaData loadMetaDataOrThrow(InputStream stream) {
        var result = loadMetaData(stream);
        if (result.error().isPresent()) {
            throw new RuntimeException(result.error().get().message());
        }
        return result.result().orElseThrow();
    }

    public static PackMetaData loadMetaDataOrThrow(Path path) {
        var result = loadMetaData(path);
        if (result.error().isPresent()) {
            throw new RuntimeException(result.error().get().message());
        }
        return result.result().orElseThrow();
    }
    public static PackMetaData metadataFromMod(IModInfo mod) {
        return new ImmutableMetaData(
            mod.getModId(),
            Optional.of(mod.getDisplayName()),
            Optional.of(mod.getDescription()),
            Optional.of(mod.getVersion()),
            List.of(),
            mod.getDependencies()
                .stream()
                .map(ContentPackUtils::dependencyFromMod)
                .toList()
        );
    }

    public static PackDependency dependencyFromMod(IModInfo.ModVersion modDep) {
        return new ImmutableDependency(
            modDep.isMandatory() ? DependencyType.REQUIRED : DependencyType.OPTIONAL,
            DependencySource.MOD,
            modDep.getModId(),
            Optional.of(modDep.getVersionRange()),
            modDep.getReferralURL().map(URL::toString),
            Optional.empty()
        );
    }

    public static void copyProbes(IModInfo mod) {
        try {
            var filePath = mod.getOwningFile().getFile().getFilePath();
            
            // 检查路径是否关联默认文件系统，如果不是则跳过
            if (!filePath.getFileSystem().equals(FileSystems.getDefault())) {
                // 对于非默认文件系统的路径，我们无法处理，直接返回
                Kubeloader.LOGGER.debug("Skipping mod {} as it's not associated with the default file system", mod.getModId());
                return;
            }
            
            var file = filePath.toFile();
            // 检查文件是否存在以及是否为文件（而不是目录）
            if (!file.exists() || file.isDirectory()) {
                Kubeloader.LOGGER.debug("Skipping mod {} as the file does not exist or is a directory", mod.getModId());
                return;
            }
            
            try (var jarFile = new JarFile(file)) {
                jarFile.stream()
                        .filter(e -> !e.isDirectory())
                        .filter(e -> e.getName().endsWith(".d.ts"))
                        .filter(e -> e.getName().startsWith(Kubeloader.FOLDER_NAME + "/" + "probe"))
                        .forEach(jarEntry -> {
                            try {
                                Path probePath = KubeJSPaths.DIRECTORY.resolve("probe");
                                Path userPath = probePath.resolve("user").resolve("contentpack").resolve(mod.getModId());

                                // 确保目标目录存在
                                Files.createDirectories(userPath);

                                // 构建目标文件路径
                                String fileName = Paths.get(jarEntry.getName()).getFileName().toString();
                                Path targetFile = userPath.resolve(fileName);

                                // 从JAR中提取文件并复制到目标位置
                                try (InputStream is = jarFile.getInputStream(jarEntry)) {
                                    Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                                }

                                Kubeloader.LOGGER.info("Copied probe file {} from mod {} to {}", fileName, mod.getModId(), targetFile);
                            } catch (IOException e) {
                                Kubeloader.LOGGER.error("Failed to copy probe file from mod JAR: {}", jarEntry.getName(), e);
                            }
                        });
            }
        } catch (UnsupportedOperationException e) {
            // 静默跳过不支持的文件系统，仅在调试模式下记录
            Kubeloader.LOGGER.debug("Skipping mod {} as it uses an unsupported file system", mod.getModId());
        } catch (IOException e) {
            // 静默跳过无法加载探针的模组，只在调试模式下记录
            Kubeloader.LOGGER.debug("Failed to load probes from mod: {}", mod.getModId(), e);
        }
    }


}
