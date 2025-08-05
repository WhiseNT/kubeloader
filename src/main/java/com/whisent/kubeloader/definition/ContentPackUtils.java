package com.whisent.kubeloader.definition;

import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.meta.dependency.DependencySource;
import com.whisent.kubeloader.impl.depends.ImmutableMetaData;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import com.whisent.kubeloader.definition.meta.dependency.DependencyType;
import com.whisent.kubeloader.impl.depends.ImmutableDependency;
import com.whisent.kubeloader.definition.meta.dependency.PackDependency;
import com.whisent.kubeloader.files.FileIO;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptPackInfo;
import net.minecraftforge.forgespi.language.IModInfo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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
}
