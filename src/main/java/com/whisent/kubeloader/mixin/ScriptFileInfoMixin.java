package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.definition.meta.Engine;
import com.whisent.kubeloader.impl.mixin.ScriptFileInfoInterface;
import com.whisent.kubeloader.utils.Debugger;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptPackInfo;
import dev.latvian.mods.kubejs.script.ScriptSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.*;
import java.util.regex.Pattern;

@Mixin(value = ScriptFileInfo.class, remap = false)
public abstract class ScriptFileInfoMixin implements ScriptFileInfoInterface {
    @Shadow @Final public ScriptPackInfo pack;
    @Unique
    private String targetPath = "";

    @Unique
    private Set<String> sides;
    
    @Unique
    private Optional<Engine> engine = Optional.empty();
    
    @Unique
    private static final Pattern MIXIN_PATTERN =
            Pattern.compile("^//mixin\\s*\\(\\s*value\\s*=\\s*\"([^\"]+)\"\\s*\\)");
    
    @Unique
    private static final Pattern SIDE_PATTERN =
            Pattern.compile("^//side\\s*:\\s*([a-zA-Z\\-]+)");
    
    @Unique
    private static final Pattern ENGINE_PATTERN =
            Pattern.compile("^//engine\\s*:\\s*([a-zA-Z]+)");
    
    @Unique
    public String kubeLoader$getTargetPath() {
        return targetPath;
    }

    @Unique
    public void kubeLoader$setTargetPath(String targetPath) {
        if (Objects.equals(this.targetPath, "")) {
            this.targetPath = targetPath;
        }
    }
    
    @Unique
    public Optional<Engine> kubeLoader$getEngine() {
        return engine;
    }
    
    @Unique
    public void kubeLoader$setEngine(Engine engine) {
        this.engine = Optional.ofNullable(engine);
    }
    


    // 在for循环内部注入代码，处理自定义注释
    @Inject(method = "preload", at = @At(value = "INVOKE",
            target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z", ordinal = 0),
            locals = LocalCapture.CAPTURE_FAILEXCEPTION)
    private void onProcessLine(ScriptSource source, CallbackInfo ci, int i, String tline) {
        // 确保 sides 被初始化
        if (sides == null) {
            sides = new HashSet<>();
        }
        
        // 在原始方法处理每一行时注入我们的逻辑
        if (!tline.isEmpty() && !tline.startsWith("import ")) {
            if (tline.startsWith("//")) {

                // 处理mixin注释
                if (tline.contains("mixin")) {
                    var mixinMatcher = MIXIN_PATTERN.matcher(tline);
                    if (mixinMatcher.find()) {
                        String mixinValue = mixinMatcher.group(1);
                        Debugger.out("找到mixin注释：" + mixinValue);
                        kubeLoader$setTargetPath(mixinValue);

                    }
                    //Debugger.out("mixin注释：" + this.getTargetPath());
                }
                
                // 处理side注释
                if (tline.contains("side")) {
                    var sideMatcher = SIDE_PATTERN.matcher(tline);
                    if (sideMatcher.find()) {
                        String sideValue = sideMatcher.group(1);
                        sides.add(sideValue);
                        Debugger.out("找到side注释：" + sideValue);
                        // 这里可以添加处理side注释的逻辑
                    }
                }
                
                // 处理engine注释
                if (tline.contains("engine")) {
                    var engineMatcher = ENGINE_PATTERN.matcher(tline);
                    if (engineMatcher.find()) {
                        String engineValue = engineMatcher.group(1);
                        try {
                            Engine engineType = Engine.valueOf(engineValue.toLowerCase());
                            kubeLoader$setEngine(engineType);
                            Debugger.out("找到engine注释：" + engineValue + " -> " + engineType);
                        } catch (IllegalArgumentException e) {
                            Debugger.out("无效的engine值：" + engineValue + "，有效值为：graaljs, rhino, default_engine, both");
                        }
                    }
                }

            }
        }
    }
    @Unique
    public Set<String> kubeLoader$getSides() {
        if (sides == null) {
            sides = new HashSet<>();
        }
        return sides;
    }

    @Inject(method = "skipLoading",at = @At("HEAD"),cancellable = true)
    private void kubeLoader$skipLoading(CallbackInfoReturnable<String> cir) {

    }

    private ScriptFileInfo thiz() {
        return (ScriptFileInfo) (Object) this;
    }
}