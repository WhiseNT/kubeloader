package com.whisent.kubeloader.impl.mod;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import dev.latvian.mods.kubejs.script.*;
import net.minecraftforge.forgespi.language.IModInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.EnumMap;
import java.util.Map;
import java.util.jar.JarFile;

/**
 * @author ZZZank
 */
public class ModContentPack implements ContentPack {
    private final IModInfo mod;
    final Map<ScriptType, ScriptPack> packs = new EnumMap<>(ScriptType.class);

    public ModContentPack(IModInfo mod) {
        this.mod = mod;
    }

    @Override
    public @NotNull String getNamespace() {
        return mod.getModId();
    }

    @Override
    @Nullable
    public ScriptPack getPack(PackLoadingContext context) {
        return packs.computeIfAbsent(context.type(), k -> createPack(context));
    }

    private ScriptPack createPack(PackLoadingContext context) {
        var pack = new ScriptPack(
            context.manager(),
            new ScriptPackInfo(mod.getModId(), "")
        );

        var prefix = Kubeloader.FOLDER_NAME + '/' + context.folderName() + '/';
        try (var file = new JarFile(mod.getOwningFile().getFile().getFilePath().toFile())) {
            var parent = file.getEntry(Kubeloader.FOLDER_NAME + '/' + context.folderName());
            if (parent == null || !parent.isDirectory()) {
                return null;
            }
            file.stream()
                .filter(e -> !e.isDirectory())
                .filter(e -> e.getName().endsWith(".js"))
                .filter(e -> e.getName().startsWith(prefix))
                .forEach(jarEntry -> {
                    var fileInfo = new ScriptFileInfo(pack.info, jarEntry.getName());
                    var scriptSource = (ScriptSource) info -> {
                        var reader = new BufferedReader(new InputStreamReader(file.getInputStream(jarEntry)));
                        return reader.lines().toList();
                    };
                    context.loadFile(pack, fileInfo, scriptSource);
                });
        } catch (IOException e) {
            return null;
        }
        return pack;
    }
}
