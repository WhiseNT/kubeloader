package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.impl.mixin_interface.ScriptFileInfoInterface;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.regex.Pattern;

@Mixin(value = ScriptFileInfo.class, remap = false)
public abstract class ScriptFileInfoMixin implements ScriptFileInfoInterface {
    @Unique
    private String targetPath = "";
    @Unique
    private static final Pattern MIXIN_PATTERN =
            Pattern.compile("^//mixin\\s*\\(\\s*value\\s*=\\s*\"([^\"]+)\"\\s*\\)");

    @Unique
    public String kubeLoader$getTargetPath() {
        return targetPath;
    }

    @Unique
    public void kubeLoader$setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    // 在for循环内部注入代码，处理自定义注释
    @Inject(method = "preload", at = @At(value = "INVOKE",
            target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z", ordinal = 0),
            locals = LocalCapture.CAPTURE_FAILEXCEPTION)
    private void onProcessLine(ScriptSource source, CallbackInfo ci, int i, String tline) {
        // 在原始方法处理每一行时注入我们的逻辑
        if (!tline.isEmpty() && !tline.startsWith("import ")) {
            if (tline.startsWith("//")) {

                // 处理mixin注释
                if (tline.contains("mixin")) {
                    var mixinMatcher = MIXIN_PATTERN.matcher(tline);
                    if (mixinMatcher.find()) {
                        String mixinValue = mixinMatcher.group(1);
                        //System.out.println("找到mixin注释：" + mixinValue);
                        kubeLoader$setTargetPath(mixinValue);
                    }
                    //System.out.println("mixin注释：" + this.getTargetPath());
                }

            }
        }
    }
}