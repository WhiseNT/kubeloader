package com.whisent.kubeloader.impl.mod;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.ContentPackProvider;
import com.whisent.kubeloader.mixin.AccessScriptManager;
import dev.architectury.platform.Mod;
import dev.latvian.mods.kubejs.script.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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

    private final Mod mod;

    public ModContentPackProvider(Mod mod) {
        this.mod = mod;
    }

    @Override
    public @Nullable ContentPack providePack() {
        return mod.getFilePaths()
            .stream()
            .map(this::scanSingle)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    private ContentPack scanSingle(Path path) {
        try (var file = new JarFile(path.toFile())) {
            if (file.getEntry(Kubeloader.FOLDER_NAME) == null) {
                return null;
            }

            var contentPack = new ModContentPack(mod);
            var entriesByType = collectEntries(file);

            for (var entry : entriesByType.entrySet()) {
                var type = entry.getKey();
                var manager = type.manager.get();
                var pack = new ScriptPack(
                    manager,
                    new ScriptPackInfo(mod.getModId(), "")
                );

                for (var jarEntry : entry.getValue()) {
                    var fileInfo = new ScriptFileInfo(pack.info, jarEntry.getName());
                    var scriptSource = (ScriptSource) info -> {
                        var reader = new BufferedReader(new InputStreamReader(file.getInputStream(jarEntry)));
                        return reader.lines().toList();
                    };
                    ((AccessScriptManager) manager).kubeLoader$loadFile(pack, fileInfo, scriptSource);
                }

                contentPack.packs.put(type, pack);
            }
            return contentPack;
        } catch (IOException e) {
            // log
            return null;
        }
    }

    private static @NotNull EnumMap<ScriptType, List<JarEntry>> collectEntries(JarFile file) {
        var entries = file.stream()
            .filter(e -> !e.isDirectory())
            .filter(e -> e.getName().endsWith(".js"))
            .filter(e -> e.getName().startsWith(Kubeloader.FOLDER_NAME + "/"))
            .toList();

        var entriesByType = new EnumMap<ScriptType, List<JarEntry>>(ScriptType.class);
        for (var entry : entries) {
            for (var matcher : PREFIXES.entrySet()) {
                if (entry.getName().startsWith(matcher.getValue())) {
                    entriesByType.computeIfAbsent(matcher.getKey(), k -> new ArrayList<>())
                        .add(entry);
                }
            }
        }
        return entriesByType;
    }
}
