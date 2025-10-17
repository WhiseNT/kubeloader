package com.whisent.kubeloader.impl;

import com.whisent.kubeloader.mixin.AccessScriptManager;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.script.*;

import java.nio.file.Path;
import java.util.Comparator;

public class CommonScriptsLoader {
    public static void loadCommonScripts(ScriptManager manager,ScriptPack pack,Path parentPath,String namespace) {
        ScriptPack newPack = new ScriptPack(manager, new ScriptPackInfo(namespace, ""));

        Path dir = parentPath.resolve("common_scripts");
        KubeJS.loadScripts(newPack, dir, "");
        var var2 = newPack.info.scripts.iterator();
        while(var2.hasNext()) {
            ScriptFileInfo fileInfo = var2.next();
            ScriptSource.FromPath scriptSource = (info) -> {
                return dir.resolve(info.file);
            };
            ((AccessScriptManager) manager)
                    .kubeLoader$loadFile(pack, fileInfo, scriptSource);
        }

        pack.scripts.sort((Comparator)null);
    }
}
