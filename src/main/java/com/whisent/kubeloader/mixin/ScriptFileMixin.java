package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.impl.mixin.ScriptFileInfoInterface;
import com.whisent.kubeloader.klm.MixinManager;
import com.whisent.kubeloader.klm.ast.AstToSourceConverter;
import com.whisent.kubeloader.klm.ast.JSInjector;
import com.whisent.kubeloader.klm.dsl.EventProbe;
import com.whisent.kubeloader.klm.dsl.MixinDSL;
import com.whisent.kubeloader.klm.dsl.MixinDSLParser;
import com.whisent.kubeloader.utils.Debugger;
import dev.latvian.mods.kubejs.script.ScriptFile;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptSource;
import dev.latvian.mods.kubejs.util.UtilsJS;
import dev.latvian.mods.rhino.Parser;
import dev.latvian.mods.rhino.ast.AstRoot;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(value = ScriptFile.class, remap = false)
public class ScriptFileMixin {
    @Shadow @Final public ScriptFileInfo info;


    @Shadow @Final public ScriptPack pack;

    @Shadow @Final public ScriptSource source;

    @Inject(method = "load", at = @At("HEAD"), cancellable = true)
    public void kubeLoader$load(CallbackInfo ci) throws Throwable {
        // 检查脚本是否包含mixin属性
        Debugger.out("加载脚本 " + this.info.location);
        Debugger.out("mixin DSL Map: " + kubeLoader$getMixinDSL().keySet());
        if (kubeLoader$getMixinDSL().getOrDefault(this.info.location,null) != null) {

            // 读取源代码
            String sourceCode = String.join("\n", this.info.lines);
            // 创建Parser并解析源代码为AST
            Parser parser = new Parser(this.pack.manager.context);
            AstRoot root = parser.parse(sourceCode, this.info.file, 0);
            AstToSourceConverter converter = new AstToSourceConverter(sourceCode);
            AtomicReference<String> modifiedSourceCode = new AtomicReference<>(sourceCode);
            kubeLoader$getMixinDSL().get(this.info.location).forEach(dsl -> {
                if (Objects.equals(dsl.getType(), "EventSubscription")) {
                    modifiedSourceCode.set(EventProbe.applyTo(modifiedSourceCode.get(),dsl));
                } else if (Objects.equals(dsl.getType(), "FunctionDeclaration")) {
                    JSInjector.injectFromDSL(root,dsl);
                    modifiedSourceCode.set(converter.convertAndFix(root, dsl.getAction()));
                }
                Debugger.out("修改后的源代码 "+ this.info.location + ":\n" + modifiedSourceCode.get());
                this.pack.manager.scriptType.console.info("Apply mixin for "+ this.info.location + " from "+dsl.getSourcePath());


            });

            // 使用修改后的源代码替换原始代码执行
            this.pack.manager.context.evaluateString(
                    this.pack.scope,
                    modifiedSourceCode.get(),
                    this.info.location,
                    1,
                    (Object)null
            );
            //kubeLoader$applyMixin();
            // 清空原始代码行以节省内存
            this.info.lines = UtilsJS.EMPTY_STRING_ARRAY;

            // 取消原始方法的执行
            ci.cancel();
        }

        //kubeLoader$applyMixin();

    }

    private void kubeLoader$applyMixin() {
        String mixinPath = ((ScriptFileInfoInterface) this.info).kubeLoader$getTargetPath();
        if (!mixinPath.isEmpty()) {
            this.pack.manager.scriptType.console.warn("You should put mixin scripts in mixins folder!");
            if (false) {
                //kubeLoader$debugLog("Detected mixin DSL target: " + mixinPath);
                // 读取源代码
                String sourceCode = String.join("\n", this.info.lines);
                List<MixinDSL> dsls = MixinDSLParser.parse(sourceCode);
                getConsole().debug("Founded " + dsls.size() + " mixin DSL Object in " + this.info.location);
                dsls.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
                dsls.forEach(dsl -> {
                    dsl.setFile((ScriptFile)(Object)this);
                    dsl.setTargetFile(mixinPath);
                    dsl.setSourcePath(this.info.location);
                    kubeLoader$addMixinDSL(mixinPath,dsl);
                    this.pack.manager.scriptType.console.info("Adding new mixin object for "+ mixinPath);
                    this.pack.manager.scriptType.console.info("  Type: "+ dsl.getType() + (dsl.getPriority() != 0 ? " Priority: "+dsl.getPriority() : ""));


                });
            }

        }
    }

    private Map<String,List<MixinDSL>> kubeLoader$getMixinDSL() {
        return MixinManager.getMixinMap();
    }

    private void kubeLoader$addMixinDSL(String path, MixinDSL dsl) {
        kubeLoader$getMixinDSL().putIfAbsent(path, new ArrayList<>());
        if (kubeLoader$getMixinDSL().get(path) != null ) {
            kubeLoader$getMixinDSL().get(path).add(dsl);
        }
    }

    private Logger getConsole() {
        return this.pack.manager.scriptType.console.logger;
    }

    private void kubeLoader$debugLog(String msg) {

    }

}