package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.mixinjs.ast.AstToSourceConverter;
import com.whisent.kubeloader.mixinjs.ast.JSInjector;
import com.whisent.kubeloader.mixinjs.dsl.EventProbe;
import com.whisent.kubeloader.mixinjs.dsl.EventProbeDSLParser;
import com.whisent.kubeloader.mixinjs.dsl.MixinDSL;
import com.whisent.kubeloader.mixinjs.dsl.MixinDSLParser;
import com.whisent.kubeloader.impl.mixin_interface.ScriptFileInfoInterface;
import com.whisent.kubeloader.impl.mixin_interface.ScriptManagerInterface;
import dev.latvian.mods.kubejs.script.ScriptFile;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptSource;
import dev.latvian.mods.kubejs.util.UtilsJS;
import dev.latvian.mods.rhino.Parser;
import dev.latvian.mods.rhino.ast.*;
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
        String mixinPath = ((ScriptFileInfoInterface) this.info).getTargetPath();
        if (!mixinPath.isEmpty()) {
            // 这是一个mixin DSL脚本，不直接执行，而是进行处理
            System.out.println("检测到Mixin DSL脚本: " + mixinPath);

            // 读取源代码
            String sourceCode = String.join("\n", this.info.lines);
            List<MixinDSL> dsls = MixinDSLParser.parse(sourceCode);
            System.out.println("解析完成，DSL数量: " + dsls.size());
            dsls.forEach(dsl -> {
                dsl.setTargetFile(mixinPath);
                dsl.setSourcePath(this.info.location);
                addMixinDSL(mixinPath,dsl);
                System.out.println("DSL类型: " + dsl.getType());
                this.pack.manager.scriptType.console.log("[Mixin]Adding new mixin target "+ mixinPath);

                System.out.println("DSL已写入: ");
                System.out.println(dsl.toString());

            });
            // 使用基于AST的解析器解析DSL脚本
            
            // MixinDSL实例已经在解析时注册，无需再次注册

            
            // 处理完DSL后，取消原始的脚本执行
        }
        if (getMixinDSL().getOrDefault(this.info.location,null) != null) {
            System.out.println("检测到Mixin对象: " + this.info.location);
            // 读取源代码
            String sourceCode = String.join("\n", this.info.lines);
            // 创建Parser并解析源代码为AST
            Parser parser = new Parser(this.pack.manager.context);
            AstRoot root = parser.parse(sourceCode, this.info.file, 0);
            AstToSourceConverter converter = new AstToSourceConverter(sourceCode);
            AtomicReference<String> modifiedSourceCode = new AtomicReference<>(sourceCode);
            getMixinDSL().get(this.info.location).forEach(dsl -> {
                if (Objects.equals(dsl.getType(), "EventSubscription")) {
                    modifiedSourceCode.set(EventProbe.applyTo(modifiedSourceCode.get(),dsl));
                    System.out.println("修改后的源代码:" + modifiedSourceCode);
                } else if (Objects.equals(dsl.getType(), "FunctionDeclaration")) {
                    JSInjector.injectFromDSL(root,dsl);
                    modifiedSourceCode.set(converter.convertAndFix(root, dsl.getAction()));
                    System.out.println("修改后的源代码:" + modifiedSourceCode);

                }
                this.pack.manager.scriptType
                        .console.log("[Mixin]Apply mixin for "+ this.info.location);

            });

            // 使用修改后的源代码替换原始代码执行
            this.pack.manager.context.evaluateString(
                    this.pack.scope,
                    modifiedSourceCode.get(),
                    this.info.location,
                    1,
                    (Object)null
            );

            // 清空原始代码行以节省内存
            this.info.lines = UtilsJS.EMPTY_STRING_ARRAY;

            // 取消原始方法的执行
            ci.cancel();
        }

    }

    private Map<String,List<MixinDSL>> getMixinDSL() {
        return ((ScriptManagerInterface)this.pack.manager).getKubeLoader$mixinDSLs();
    }

    private void addMixinDSL(String path, MixinDSL dsl) {
        getMixinDSL().putIfAbsent(path, new ArrayList<>());
        if (getMixinDSL().get(path) != null ) {
            getMixinDSL().get(path).add(dsl);
        }
    }

}