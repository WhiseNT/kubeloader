package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.impl.mixin_interface.ScriptFileInfoInterface;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptSource;
import dev.latvian.mods.kubejs.util.UtilsJS;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Mixin(value = ScriptFileInfo.class, remap = false)
public abstract class ScriptFileInfoMixin implements ScriptFileInfoInterface {
    @Shadow public String[] lines;

    @Shadow @Final private Map<String, List<String>> properties;

    @Shadow public abstract List<String> getProperties(String s);

    @Unique
    private static final Pattern MIXIN_PATTERN =
            Pattern.compile("^//mixin\\s*\\(\\s*value\\s*=\\s*\"([^\"]+)\"\\s*\\)");

    @Unique
    private static final Pattern SIDE_PATTERN = Pattern.compile("^//side:(\\w+)");

    @Unique
    private List<String> sides = new ArrayList<>();

    @Unique
    private boolean needToLoad = true;
    
    @Inject(method = "preload", at = @At("HEAD"))
    public void kubeLoader$preload(ScriptSource source, CallbackInfo ci) throws IOException {
        lines = source.readSource((ScriptFileInfo) ((Object)this)).toArray(UtilsJS.EMPTY_STRING_ARRAY);

        for (int i = 0; i < this.lines.length; i++) {
            var tline = lines[i].trim();

            // 解析mixin注释
            if (tline.startsWith("//mixin")) {
                var mixinMatcher = MIXIN_PATTERN.matcher(tline);
                if (mixinMatcher.find()) {
                    String mixinValue = mixinMatcher.group(1);
                    this.properties.computeIfAbsent("mixin", k -> new ArrayList<>()).add(mixinValue);
                }
            }

            // 解析side注释
            if (tline.startsWith("//side:")) {
                var sideMatcher = SIDE_PATTERN.matcher(tline);
                if (sideMatcher.find()) {
                    String sideValue = sideMatcher.group(1);
                    sides.add(sideValue);
                }
            }
        }
    }

    @Override
    public boolean shouldLoad(ScriptManager scriptManager, ScriptSource source) throws IOException {
        lines = source.readSource((ScriptFileInfo) ((Object)this)).toArray(UtilsJS.EMPTY_STRING_ARRAY);

        for (int i = 0; i < this.lines.length; i++) {
            var tline = lines[i].trim();
            // 解析side注释
            if (tline.startsWith("//side:")) {
                var sideMatcher = SIDE_PATTERN.matcher(tline);
                if (sideMatcher.find()) {
                    String sideValue = sideMatcher.group(1);
                    sides.add(sideValue);
                }
            }
        }
        // 如果没有指定side，则默认加载
        if (sides.isEmpty()) {
            return true;
        }

        // startup脚本在所有环境中都加载
        if (sides.contains("startup")) {
            System.out.println("正在加载startup脚本：");
            return true;
        }

        // 检查当前环境是否应该加载脚本
        for (String side : sides) {
            if ("common".equals(side)) {
                return true;
            }
            if (scriptManager.scriptType.isClient() && "client".equals(side)) {
                System.out.println("正在加载client脚本：");
                return true;
            }
            if (scriptManager.scriptType.isServer() && "server".equals(side)) {
                System.out.println("正在加载server脚本：");
                return true;
            }
        }
        
        // 如果没有匹配的side，则不加载
        System.out.println("没有匹配的side，不加载脚本：");
        return false;
    }

    public String getMixinProperty() {
        return ((ScriptFileInfo) ((Object)this)).getProperty("mixin", "");
    }

    public boolean isNeedToLoad() {
        return needToLoad;
    }

    private AccessScriptFileInfo getAccess() {
        return (AccessScriptFileInfo) (Object) this;
    }

}