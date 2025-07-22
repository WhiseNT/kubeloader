package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.impl.mixin_interface.ScriptFileInfoInterface;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptSource;
import dev.latvian.mods.kubejs.util.UtilsJS;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
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
    @Unique private String mixin;

    @Shadow @Final private Map<String, List<String>> properties;

    @Shadow public abstract List<String> getProperties(String s);

    @Unique
    private static final Pattern MIXIN_PATTERN =
            Pattern.compile("^//mixin\\s*\\(\\s*value\\s*=\\s*\"([^\"]+)\"\\s*\\)");
    @Inject(method = "preload", at = @At("HEAD"))
    public void kubeLoader$preload(ScriptSource source, CallbackInfo ci) throws IOException {
        lines = source.readSource((ScriptFileInfo) ((Object)this)).toArray(UtilsJS.EMPTY_STRING_ARRAY);

        for (int i = 0; i < this.lines.length; i++) {
            var tline = lines[i].trim();

            if (tline.startsWith("//mixin")) {
                System.out.println("找到Mixin文件");
                var mixinMatcher = MIXIN_PATTERN.matcher(tline);
                System.out.println(mixinMatcher);
                if (mixinMatcher.find()) {
                    System.out.println("匹配成功");
                    String mixinValue = mixinMatcher.group(1);
                    this.getAccess()
                        .getProperties()
                        .computeIfAbsent("mixin", k -> new ArrayList<>()).add(mixinValue);
                    System.out.println(properties.get("mixin"));
                }
            }
            this.mixin = ((ScriptFileInfo) ((Object)this)).getProperty("mixin", "");
        }
    }

    public String getMixinProperty() {
        return this.mixin;
    }

    private AccessScriptFileInfo getAccess() {
        return (AccessScriptFileInfo) (Object) this;
    }

}
