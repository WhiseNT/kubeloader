package com.whisent.kubeloader.files;

import com.whisent.kubeloader.Kubeloader;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.*;
import dev.latvian.mods.kubejs.util.KubeJSPlugins;
import dev.latvian.mods.kubejs.util.LogType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.Comparator;
import java.util.Iterator;

public class ContentScriptsManager extends ScriptManager {
    public ContentScriptsManager(ScriptType t) {
        super(t);
    }

    @Override
    public void load() {
        super.load();
    }

    @Override
    public void reload(@Nullable ResourceManager resourceManager) {

        unload();

        loadFromDirectory();

        load();
        //super.reload(resourceManager);
    }

    @Override
    public void loadFromDirectory() {
        Kubeloader.LOGGER.info((this.scriptType.name+"脚本正在加载"));
        if (Files.notExists(Kubeloader.PackPath.resolve("namespace").resolve(this.scriptType.name + "_scripts"), new LinkOption[0])) {
            Exception ex;
            try {
                Files.createDirectories(Kubeloader.PackPath.resolve("namespace").resolve(this.scriptType.name + "_scripts"));
            } catch (Exception var6) {
                ex = var6;
                this.scriptType.console.error("Failed to create script directory", ex);
            }

            try {
                OutputStream out = Files.newOutputStream(Kubeloader.PackPath.resolve("namespace").resolve(this.scriptType.name + "_scripts").resolve("example.js"));

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

        ScriptPack pack = new ScriptPack(this, new ScriptPackInfo(Kubeloader.PackPath.resolve("namespace").resolve(this.scriptType.name + "_scripts")
                .getFileName().toString(), ""));
        KubeJS.loadScripts(pack, Kubeloader.PackPath.resolve("namespace").resolve(this.scriptType.name + "_scripts"), "");
        Iterator var2 = pack.info.scripts.iterator();

        while(var2.hasNext()) {
            ScriptFileInfo fileInfo = (ScriptFileInfo)var2.next();
            ScriptSource.FromPath scriptSource = (info) -> {
                return Kubeloader.PackPath.resolve("namespace").resolve(this.scriptType.name + "_scripts").resolve(info.file);
            };
            this.loadFile(pack, fileInfo, scriptSource);
        }

        pack.scripts.sort((Comparator)null);
        this.packs.put(pack.info.namespace, pack);
    }
    private void loadFile(ScriptPack pack, ScriptFileInfo fileInfo, ScriptSource source) {
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
