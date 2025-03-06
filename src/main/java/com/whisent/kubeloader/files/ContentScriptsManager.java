package com.whisent.kubeloader.files;

import com.mojang.datafixers.util.Either;
import com.whisent.kubeloader.Kubeloader;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.*;
import dev.latvian.mods.kubejs.util.ClassFilter;
import dev.latvian.mods.kubejs.util.KubeJSPlugins;
import dev.latvian.mods.kubejs.util.LogType;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.NativeJavaClass;
import dev.latvian.mods.rhino.Scriptable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;

public class ContentScriptsManager extends ScriptManager {

    public ContentScriptsManager(ScriptType t) {
        super(t);
    }



    @Override
    public void reload(@Nullable ResourceManager resourceManager) {

        unload();

        loadFromDirectory();

        load();
        Kubeloader.LOGGER.debug("加载自定义脚本成功");
    }

    private void loadFromMods() {

    }

    private void loadFromResources(ResourceManager resourceManager) {
        Map<String, List<ResourceLocation>> packMap = new HashMap<>();

        for (var resource : resourceManager.listResources("contentpack", s -> s.getPath().endsWith(".js") || s.getPath().endsWith(".ts") && !s.getPath().endsWith(".d.ts")).keySet()) {
            packMap.computeIfAbsent(resource.getNamespace(), s -> new ArrayList<>()).add(resource);
        }
        //不应该从resourceManager获取 应该直接从jar中获取entry
        for (var entry : packMap.entrySet()) {
            var pack = new ScriptPack(this, new ScriptPackInfo(entry.getKey(), "contentpack/"));

            for (var id : entry.getValue()) {
                pack.info.scripts.add(new ScriptFileInfo(pack.info, id.getPath().substring(7)));
            }

            for (var fileInfo : pack.info.scripts) {
                var scriptSource = (ScriptSource.FromResource) info -> resourceManager.getResourceOrThrow(info.id);
                loadFile(pack, fileInfo, scriptSource);
            }

            pack.scripts.sort(null);
            packs.put(pack.info.namespace, pack);
        }
    }
    @Override
    public void loadFromDirectory() {
        Path ScriptsPath = Kubeloader.PackPath.resolve("namespace").resolve(this.scriptType.name + "_scripts");
        Kubeloader.LOGGER.debug(ScriptsPath.toString());
        Kubeloader.LOGGER.info((this.scriptType.name+"脚本正在加载"));
        if (Files.notExists(ScriptsPath, new LinkOption[0])) {
            Exception ex;
            try {
                Files.createDirectories(ScriptsPath);
            } catch (Exception var6) {
                ex = var6;
                this.scriptType.console.error("Failed to create script directory", ex);
            }

            try {
                OutputStream out = Files.newOutputStream(ScriptsPath.resolve("example.js"));

                try {
                    out.write(("// priority: 0\n\n// Visit the wiki for more info - https://kubejs.com/\n\nconsole.info('Hello, World! (Loaded " + this.scriptType.name + "_scripts" + " scripts)')\n\n").getBytes(StandardCharsets.UTF_8));
                } catch (Throwable var7) {
                    if (out != null) {
                        try {
                            out.close();
                        } catch (Throwable var5) {
                            var7.addSuppressed(var5);
                        }
                    }

                    throw var7;
                }

                if (out != null) {
                    out.close();
                }
            } catch (Exception var8) {
                ex = var8;
                this.scriptType.console.error("Failed to write example.js", ex);
            }
        }

        ScriptPack pack = new ScriptPack(this, new ScriptPackInfo(ScriptsPath
                .getFileName().toString(), ""));
        Kubeloader.LOGGER.debug(pack.toString());
        KubeJS.loadScripts(pack, ScriptsPath, "");
        Iterator var2 = pack.info.scripts.iterator();

        while(var2.hasNext()) {
            ScriptFileInfo fileInfo = (ScriptFileInfo)var2.next();
            ScriptSource.FromPath scriptSource = (info) -> {
                return ScriptsPath.resolve(info.file);
            };
            this.loadFile(pack, fileInfo, scriptSource);
        }

        pack.scripts.sort((Comparator)null);
        //
        this.packs.put(pack.info.namespace, pack);
    }
    public void loadFile(ScriptPack pack, ScriptFileInfo fileInfo, ScriptSource source) {
        try {
            fileInfo.preload(source);
            String skip = fileInfo.skipLoading();
            if (skip.isEmpty()) {
                pack.scripts.add(new ScriptFile(pack, fileInfo, source));
            } else {
                this.scriptType.console.info("Skipped " + fileInfo.location + ": " + skip);
            }
        } catch (Throwable var5) {
            Throwable error = var5;
            this.scriptType.console.error("Failed to pre-load script file '" + fileInfo.location + "'", error);
        }

    }
}
