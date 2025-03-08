package com.whisent.kubeloader.impl.mod;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.ContentPackProvider;
import dev.latvian.mods.kubejs.script.*;
import net.minecraftforge.forgespi.language.IModInfo;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
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
            var entry = file.getEntry(Kubeloader.FOLDER_NAME);
            if (entry == null || !entry.isDirectory()) {
                return null;
            }
            return new ModContentPack(mod);
        } catch (IOException e) {
            // log
            return null;
        }
    }

    @Override
    public String toString() {
        return "ModContentPackProvider[mod=%s]".formatted(mod.getModId());
    }
}
