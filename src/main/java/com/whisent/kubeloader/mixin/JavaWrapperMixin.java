package com.whisent.kubeloader.mixin;

import dev.latvian.mods.kubejs.bindings.JavaWrapper;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.typings.Info;
import dev.latvian.mods.rhino.util.RemapForJS;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(JavaWrapper.class)
public class JavaWrapperMixin {
    @Shadow @Final private ScriptManager manager;

    @Unique
    @RemapForJS("type")
    @Info("""
    This method is equivalent to method "loadClass"
    """
    )
    public void kubeLoader$type(String className) {
        manager.loadJavaClass(className, true);
    }
}
