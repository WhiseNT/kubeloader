package com.whisent.kubeloader.impl.path;

import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.impl.ContentPackProviders;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class PathContentPackRepositorySource implements RepositorySource {
    private final PackType packType;
    private final PackSource sourceInfo;

    public PathContentPackRepositorySource(PackType packType) {
        this.packType = packType;
        this.sourceInfo = PackSource.create(
            (name) -> Component.literal("KubeLoader"),
            true
        );
    }

    @Override
    public void loadPacks(@NotNull Consumer<Pack> consumer) {
        // 遍历所有PathContentPack并为它们创建资源包
        for (ContentPack contentPack : ContentPackProviders.getPacks()) {
            if (contentPack instanceof PathContentPack pathContentPack) {
                String packId = "kubeloader/" + pathContentPack.id();
                Component displayName = Component.literal(pathContentPack.id());
                
                Pack pack = Pack.readMetaAndCreate(
                    packId, displayName,
                    true, 
                    name -> new PathContentPackResources(name, pathContentPack),
                    this.packType, Pack.Position.TOP, this.sourceInfo
                );
                
                if (pack != null) {
                    consumer.accept(pack);
                }
            }
        }
    }
}