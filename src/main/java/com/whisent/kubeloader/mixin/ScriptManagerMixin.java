package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.files.FileIO;
import com.whisent.kubeloader.impl.ContentPackProviders;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.script.*;
import net.minecraftforge.forgespi.language.IModInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;



@Mixin(ScriptManager.class)
public abstract class ScriptManagerMixin {
    @Shadow
    @Final
    public ScriptType scriptType;

    @Shadow
    protected abstract void loadFile(ScriptPack pack, ScriptFileInfo fileInfo, ScriptSource source);

    @Shadow
    @Final
    public Map<String, ScriptPack> packs;

    @Inject(method = "reload", at = @At(value = "INVOKE", target = "Ldev/latvian/mods/kubejs/script/ScriptManager;load()V"), remap = false)
    private void injectPacks(CallbackInfo ci) {
        var context = new PackLoadingContext(thiz());
        for (var contentPack : ContentPackProviders.getPacks()) {
            var pack = contentPack.getPack(context);
            if (pack != null) {
                this.packs.put(contentPack.getNamespace(context), pack);
            }
        }

        //TODO: 把这个也改为 ContentPackProvider
        FileIO.listDirectories(Kubeloader.PackPath).forEach(namespace -> {
            Path ScriptsPath = Kubeloader.PackPath.resolve(namespace).resolve(this.scriptType.name + "_scripts");
            Kubeloader.LOGGER.debug(ScriptsPath.toString());
            Kubeloader.LOGGER.info("{}脚本正在加载", this.scriptType.name);
            ScriptPack pack = new ScriptPack(
                thiz(),
                new ScriptPackInfo(ScriptsPath.getFileName().toString(), "")
            );
            Kubeloader.LOGGER.debug(pack.toString());
            KubeJS.loadScripts(pack, ScriptsPath, "");
            for (ScriptFileInfo fileInfo : pack.info.scripts) {
                ScriptSource.FromPath scriptSource = (info) -> ScriptsPath.resolve(info.file);
                this.loadFile(pack, fileInfo, scriptSource);
            }
            pack.scripts.sort(null);
            this.packs.put(namespace + this.scriptType.name, pack);
        });
    }

    @Unique
    private ScriptManager thiz() {
        return (ScriptManager) (Object) this;
    }
}
