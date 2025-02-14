package com.whisent.kubeloader.files;

import com.whisent.kubeloader.Kubeloader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class ResourcePackProvider  implements RepositorySource {

    private final Path resourceDir;
    private final PackType type;
    private final List<File> directories;
    private final PackSource sourceInfo;
    public ResourcePackProvider(Path resourceDir, PackType type) {
        this.resourceDir = resourceDir;
        this.type = type;
        this.sourceInfo = PackSource.create((name)-> Component.literal("KubeLoader"),
                true);
        this.directories = new ArrayList<>();
        this.directories.add(this.resourceDir.resolve(type.getDirectory()).toFile());

        for (File directory : directories) {
            if (!directory.exists()) {
                directory.mkdirs();
            }
            if (!directory.isDirectory()) {
                throw new IllegalStateException("加载失败" + " from non-directory. " + directory.getAbsolutePath());
            }
        }
    }


    @Override
    public void loadPacks(Consumer<Pack> consumer) {
        //遍历所有子目录
        for (File directory : directories) {
            //遍历目录子文件
            for (File file : Objects.requireNonNull(directory.listFiles())) {
                final boolean isFolderPack =  Files.isDirectory(file.toPath());
                if (isFolderPack) {
                    final String packName = file.getName();
                    final Component displayName = Component.literal(packName);
                    final Pack pack = Pack.readMetaAndCreate(packName, displayName,
                            true, createPackSupplier(file),
                            this.type, Pack.Position.TOP, this.sourceInfo);
                    if (pack != null) {
                        consumer.accept(pack);
                        Kubeloader.LOGGER.info("资源包加载成功");
                    }
                    else {
                        Kubeloader.LOGGER.info("跳过资源加载");
                    }
                }
            }
        }
    }
    private Pack.ResourcesSupplier createPackSupplier (File packFile) {
        return name -> packFile.isDirectory() ? new PathPackResources(name, packFile.toPath(), false) : new FilePackResources(name, packFile, false);
    }
}
