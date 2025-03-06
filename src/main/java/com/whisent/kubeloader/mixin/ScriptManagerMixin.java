package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.files.ContentScriptSources;
import com.whisent.kubeloader.files.jarFix;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.script.*;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static com.whisent.kubeloader.files.ContentPackExplorer.*;

@Mixin(ScriptManager.class)
public abstract class ScriptManagerMixin {
    @Shadow @Final public ScriptType scriptType;

    @Shadow protected abstract void loadFile(ScriptPack pack, ScriptFileInfo fileInfo, ScriptSource source);

    @Shadow @Final public Map<String, ScriptPack> packs;
    @Unique
    private Boolean kubeloader$sign = false;
    @Inject(method = "load",at = @At("HEAD"),remap = false)
    private void loadScripts(CallbackInfo ci) {

        if (!kubeloader$sign) {

            if (Objects.equals(this.scriptType.name, "startup")){
            }

        }
    }


    @Inject(method = "reload",at =
    @At(value = "INVOKE", target = "Ldev/latvian/mods/kubejs/script/ScriptManager;load()V"),remap = false)
    private void relaoadMixin(CallbackInfo ci) {
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
        ScriptPack pack = new ScriptPack(((ScriptManager)((Object) this)), new ScriptPackInfo(ScriptsPath
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
        this.packs.put("namespace", pack);

    }
    @Inject(method = "reload",at =
    @At(value = "INVOKE", target = "Ldev/latvian/mods/kubejs/script/ScriptManager;load()V"),remap = false)
    private void reloadFromModMixin(CallbackInfo ci) {
        //模组
        ModList.get().getMods().forEach(mod -> {
            try {
                Path jarPath = getModJarPath(mod);
                if (jarPath != null) {
                    Kubeloader.LOGGER.debug("找到模组"+mod);
                    var pack = new ScriptPack((ScriptManager)((Object) this), new ScriptPackInfo(mod.getModId(), ""));
                    try (JarFile jar = new JarFile(jarPath.toFile())) {
                        Enumeration<JarEntry> entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry entry = entries.nextElement();
                            //Kubeloader.LOGGER.info(String.valueOf(entry));
                            Path scriptPath = Path.of("contentpack/"+this.scriptType.name+"_scripts/");
                            Path entryPath = Path.of(entry.getName());
                            if (isContentPackFile(entry) && entryPath.startsWith(scriptPath)) {
                                Kubeloader.LOGGER.info("找到jar内文件");
                                Kubeloader.LOGGER.info(String.valueOf(entryPath));
                                pack.info.scripts.add(new ScriptFileInfo(pack.info, entry.getName()));
                                var scriptSource = (ContentScriptSources.FromMod) info -> new jarFix(jar,entry,this.scriptType);
                                loadFile(pack,new ScriptFileInfo(pack.info, entry.getName()), scriptSource);
                            }
                        }
                        pack.scripts.sort(null);
                        packs.put(mod.getModId(), pack);
                    } catch (IOException e) {
                        System.err.println("Failed to open JAR: " + jarPath);
                    }

                }

            } catch (Exception e) {
                System.err.println("Error processing mod: " + mod.getModId());
                e.printStackTrace();
            }

        });
    }
    private static List<String> countLinesInJarEntry(JarFile jarFile, JarEntry entry) throws IOException {
        if (entry.isDirectory()) {
            throw new IllegalArgumentException("JarEntry is a directory and cannot be read as a file.");
        }

        try (InputStream inputStream = jarFile.getInputStream(entry);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            var list = new ArrayList<String>();
            while ((line = reader.readLine()) != null) {
                list.add(line);
            }
            return list;
        }
    }

}
