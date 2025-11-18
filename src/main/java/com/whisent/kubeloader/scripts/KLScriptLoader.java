package com.whisent.kubeloader.scripts;

import com.whisent.kubeloader.klm.ast.AstToSourceConverter;
import com.whisent.kubeloader.klm.ast.JSInjector;
import com.whisent.kubeloader.klm.dsl.EventProbe;
import com.whisent.kubeloader.klm.dsl.MixinDSL;
import com.whisent.kubeloader.utils.Debugger;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.rhino.Parser;
import dev.latvian.mods.rhino.ast.AstRoot;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class KLScriptLoader {

    public static void load(ScriptPack pack, ScriptFileInfo info,
                            Map<String, List<MixinDSL>> mixinMap, CallbackInfo ci) {
        String sourceCode = String.join("\n", info.lines);
        //先处理KLM
        if (mixinMap.getOrDefault(info.location,null)  != null ) {
            sourceCode = applyMixin(pack,info,mixinMap,sourceCode);
        }
        //根据文件后缀进行处理
        if (isTsFile(info.file)) {
            Debugger.out("正在处理 " + info.location + " 文件");
            //typescript擦除类型

            sourceCode = TsEraser.eraseTypes(sourceCode);
            Debugger.out("修改后的源代码(TS→ES6) " + info.location + ":\n" + sourceCode);
            //转换es6语法
            sourceCode = ModernJSParser.parse(sourceCode);
            Debugger.out("修改后的源代码(ES6→ES5) " + info.location + ":\n" + sourceCode);
        } else if (isJsFile(info.file)) {
            sourceCode = ModernJSParser.parse(sourceCode);
            Debugger.out("修改后的源代码(ES5兼容运行) " + info.location + ":\n" + sourceCode);
        }
        evalString(pack,info,sourceCode);
        ci.cancel();
    }

    public static String applyMixin(ScriptPack pack,ScriptFileInfo info,
                                    Map<String, List<MixinDSL>> mixinMap,String sourceCode) {
        Parser parser = new Parser(pack.manager.context);
        AstRoot root = parser.parse(sourceCode, info.file, 0);
        AstToSourceConverter converter = new AstToSourceConverter(sourceCode);
        AtomicReference<String> modifiedSourceCode = new AtomicReference<>(sourceCode);
        mixinMap.get(info.location).forEach(dsl -> {
            if (Objects.equals(dsl.getType(), "EventSubscription")) {
                modifiedSourceCode.set(EventProbe.applyTo(modifiedSourceCode.get(), dsl));
            } else if (Objects.equals(dsl.getType(), "FunctionDeclaration")) {
                JSInjector.injectFromDSL(root, dsl);
                modifiedSourceCode.set(converter.convertAndFix(root, dsl.getAction()));
            }
            Debugger.out("修改后的源代码 " + info.location + ":\n" + modifiedSourceCode.get());
            pack.manager.scriptType.console.info("Apply mixin for " + info.location + " from " + dsl.getSourcePath());
        });
        return modifiedSourceCode.get();
    }

    public static boolean isTsFile(String file) {
        return file.endsWith(".ts");
    }
    public static boolean isJsFile(String file) {
        return file.endsWith(".js");
    }
    public static void evalString(ScriptPack pack,ScriptFileInfo info,String code) {
        pack.manager.context.evaluateString(
                pack.scope,
                code,
                info.location,
                1,
                (Object)null
        );
    }
}
