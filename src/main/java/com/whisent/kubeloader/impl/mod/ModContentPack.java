package com.whisent.kubeloader.impl.mod;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPackUtils;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import com.whisent.kubeloader.impl.ContentPackBase;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptSource;
import net.minecraftforge.forgespi.language.IModInfo;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.jar.JarFile;

/**
 * @author ZZZank
 */
public class ModContentPack extends ContentPackBase {
    private final IModInfo mod;

    public ModContentPack(IModInfo mod, PackMetaData metaData) {
        super(metaData);
        this.mod = mod;
    }

    @Override
    @Nullable
    protected ScriptPack createPack(PackLoadingContext context) {
        var pack = ContentPackUtils.createEmptyPack(context, id());

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

            return pack;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void loadCommonScripts(ScriptPack pack, PackLoadingContext context) {
        var commonPack = ContentPackUtils.createEmptyPack(context, id());
        var prefix = Kubeloader.FOLDER_NAME + "/common_scripts/";
        try (var file = new JarFile(mod.getOwningFile().getFile().getFilePath().toFile())) {
            file.stream()
                .filter(e -> !e.isDirectory())
                .filter(e -> e.getName().endsWith(".js"))
                .filter(e -> e.getName().startsWith(prefix))
                .forEach(jarEntry -> {
                    var fileInfo = new ScriptFileInfo(commonPack.info, jarEntry.getName());
                    var scriptSource = (ScriptSource) info -> {
                        var reader = new BufferedReader(new InputStreamReader(file.getInputStream(jarEntry)));
                        return reader.lines().toList();
                    };
                    context.loadFile(pack, fileInfo, scriptSource);
                });
            pack.info.scripts.addAll(commonPack.info.scripts);
        } catch (IOException e) {
            // TODO: log
        }
    }

    @Override
    public String toString() {
        return "ModContentPack[mod=%s]".formatted(mod.getModId());
    }
}
