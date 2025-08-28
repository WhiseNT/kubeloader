package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.impl.mixin_interface.ScriptFileInfoInterface;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptSource;
import dev.latvian.mods.kubejs.util.UtilsJS;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(value = ScriptFileInfo.class, remap = false)
public abstract class ScriptFileInfoMixin implements ScriptFileInfoInterface {
    @Shadow public String[] lines;

    @Unique
    private String targetPath = "";

    @Shadow public abstract List<String> getProperties(String s);

    @Shadow @Final private Map<String, List<String>> properties;
    @Shadow private int priority;
    @Shadow private boolean ignored;
    @Final
    @Shadow private static Pattern PROPERTY_PATTERN;
    @Unique
    private static final Pattern MIXIN_PATTERN =
            Pattern.compile("^//mixin\\s*\\(\\s*value\\s*=\\s*\"([^\"]+)\"\\s*\\)");

    @Unique
    private static final Pattern SIDE_PATTERN = Pattern.compile("^//side:(\\w+)");

    @Unique
    private List<String> sides = new ArrayList<>();

    @Unique
    private boolean needToLoad = true;

    /**
     * @author
     * @reason
     */
    @Overwrite
    public void preload(ScriptSource source) throws Throwable {
        this.properties.clear();
        this.priority = 0;
        this.ignored = false;
        this.lines = (String[]) source.readSource((ScriptFileInfo) (Object)this).toArray(UtilsJS.EMPTY_STRING_ARRAY);

        for (int i = 0; i < this.lines.length; ++i) {
            String tline = this.lines[i].trim();
            if (!tline.isEmpty() && !tline.startsWith("import ")) {
                if (tline.startsWith("//")) {
                    // 这里是你的注入点 - 在matcher创建之前
                    System.out.println("处理注释行: " + tline);
                    System.out.println("行号: " + i);

                    // 这里可以添加你的自定义逻辑
                    if (tline.contains("mixin")) {
                        var mixinMatcher = MIXIN_PATTERN.matcher(tline);
                        if (mixinMatcher.find()) {
                            String mixinValue = mixinMatcher.group(1);
                            System.out.println("找到mixin注释："+mixinValue);
                            // 使用重定向的方法确保添加元素到可变列表
                            setTargetPath(mixinValue);
                        }
                        System.out.println("mixin注释："+this.getTargetPath());
                    }
                    if (tline.startsWith("//side:")) {
                        var sideMatcher = SIDE_PATTERN.matcher(tline);
                        if (sideMatcher.find()) {
                            String sideValue = sideMatcher.group(1);
                            sides.add(sideValue);
                        }
                    }

                    Matcher matcher = PROPERTY_PATTERN.matcher(tline.substring(2).trim());
                    if (matcher.find()) {
                        ((List) this.properties.computeIfAbsent(matcher.group(1).trim(), (k) -> {
                            return new ArrayList();
                        })).add(matcher.group(2).trim());
                    }

                    this.lines[i] = "";
                }
            } else {
                this.lines[i] = "";
            }
        }

        // 还可以在方法最后添加其他逻辑
        System.out.println("预处理完成，共处理了 " + this.lines.length + " 行");
    }
    
    @Unique
    public void setTargetPath(String value) {
        targetPath = value;
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
            if (scriptManager.scriptType.isStartup() && "startup".equals(side)) {
                System.out.println("正在加载startup脚本：");
                return true;
            }
        }
        
        // 如果没有匹配的side，则不加载
        System.out.println("没有匹配的side，不加载脚本：");
        return false;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public boolean isNeedToLoad() {
        return needToLoad;
    }

    private AccessScriptFileInfo getAccess() {
        return (AccessScriptFileInfo) (Object) this;
    }

}