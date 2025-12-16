package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.graal.DynamicGraalConsole;
import com.whisent.kubeloader.graal.context.IdentifiedContext;
import com.whisent.kubeloader.impl.mixin.GraalPack;
import dev.latvian.mods.kubejs.script.ScriptPack;
import org.graalvm.polyglot.Context;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ScriptPack.class, remap = false)
public class ScriptPackMixin implements GraalPack {

    @Unique
    private Context kubeLoader$graalContext;
    @Unique
    private DynamicGraalConsole kubeLoader$dynamicGraalConsole;
    @Unique
    public void kubeLoader$setGraalContext(Context context) {

        context.getBindings("js").putMember("console",thiz().manager.scriptType.console);
        this.kubeLoader$graalContext = context;

    }

    @Override
    public Context kubeLoader$getGraalContext() {
        return kubeLoader$graalContext;
    }
    @Override
    public DynamicGraalConsole kubeLoader$getDynamicGraalConsole() {
        return kubeLoader$dynamicGraalConsole;
    }
    @Unique
    public void kubeLoader$setDynamicGraalConsole(DynamicGraalConsole console) {
        this.kubeLoader$dynamicGraalConsole = console;
    }

    private ScriptPack thiz() {
        return (ScriptPack) (Object) this;
    }
}
