package com.whisent.kubeloader.impl.mod;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.ContentPackProvider;
import dev.latvian.mods.kubejs.script.*;
import net.minecraftforge.forgespi.language.IModInfo;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * @author ZZZank
 */
public class ModContentPackProvider implements ContentPackProvider {
    private static final Map<ScriptType, String> PREFIXES = new EnumMap<>(ScriptType.class);

    static {
        for (var scriptType : ScriptType.values()) {
            PREFIXES.put(scriptType, Kubeloader.FOLDER_NAME + '/' + scriptType.name + '/');
        }
    }

    private final IModInfo mod;

    public ModContentPackProvider(IModInfo mod) {
        this.mod = mod;
    }

    @Override
    public boolean isDynamic() {
        return false;
    }

    @Override
    public @Nullable ContentPack providePack() {
        var path = mod.getOwningFile()
            .getFile()
            .getFilePath();
        return scanSingle(path);
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
}
