package com.whisent.kubeloader.impl.mod;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.ContentPackProvider;
import com.whisent.kubeloader.definition.ContentPackUtils;
import net.minecraftforge.forgespi.language.IModInfo;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.jar.JarFile;

/**
 * @author ZZZank
 */
public class ModContentPackProvider implements ContentPackProvider {

    private final IModInfo mod;

    public ModContentPackProvider(IModInfo mod) {
        this.mod = mod;
    }

    @Override
    public boolean isDynamic() {
        return false;
    }

    @Override
    public @NotNull Collection<? extends @NotNull ContentPack> providePack() {
        var path = mod.getOwningFile()
            .getFile()
            .getFilePath();
        var got = scanSingle(path);
        return got == null ? List.of() : List.of(got);
    }

    private ContentPack scanSingle(Path path) {
        try (var file = new JarFile(path.toFile())) {
            var entry = file.getEntry(Kubeloader.FOLDER_NAME + '/' + Kubeloader.META_DATA_FILE_NAME);
            if (entry == null) {
                return null;
            } else if (entry.isDirectory()) {
                throw new RuntimeException(String.format(
                        "%s should be a file, but got a directory",
                        Kubeloader.META_DATA_FILE_NAME
                ));
            }
            return new ModContentPack(mod, ContentPackUtils.loadMetaDataOrThrow(file.getInputStream(entry)));
        } catch (Exception e) {
            // log
            Kubeloader.LOGGER.error("Error when searching for ModContentPack in mod '{}'", mod.getModId(), e);
            return null;
        }
    }

    @Override
    public String toString() {
        return "ModContentPackProvider[mod=%s]".formatted(mod.getModId());
    }
}