package com.whisent.kubeloader.impl;

import com.whisent.kubeloader.mixin.AccessScriptManager;
import dev.latvian.mods.kubejs.script.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class CommonScriptsLoader {
    public static void loadCommonScripts(ScriptManager manager,ScriptPack pack,Path parentPath,String namespace) {
        Path dir = parentPath.resolve("common_scripts");
        if (!Files.isDirectory(dir)) {
            return;
        }

        ScriptPack newPack = new ScriptPack(manager, new ScriptPackInfo(namespace, ""));
        manager.collectScripts(newPack, dir, "");

        for (ScriptFileInfo fileInfo : newPack.info.scripts) {
            ((AccessScriptManager) manager).kubeLoader$loadFile(pack, fileInfo);
        }

        pack.scripts.sort((Comparator)null);
    }
}
