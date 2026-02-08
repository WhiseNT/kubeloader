package com.whisent.kubeloader.scripts;

import com.whisent.kubeloader.ConfigManager;
import com.whisent.kubeloader.compat.GraalJSCompat;
import com.whisent.kubeloader.graal.DynamicGraalConsole;
import com.whisent.kubeloader.graal.GraalApi;
import com.whisent.kubeloader.graal.event.GraalEventHandlerProxy;
import com.whisent.kubeloader.impl.mixin.GraalPack;
import com.whisent.kubeloader.klm.ast.AstToSourceConverter;
import com.whisent.kubeloader.klm.ast.JSInjector;
import com.whisent.kubeloader.klm.dsl.EventProbe;
import com.whisent.kubeloader.klm.dsl.MixinDSL;
import com.whisent.kubeloader.utils.Debugger;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.rhino.Parser;
import dev.latvian.mods.rhino.ast.AstRoot;
import org.graalvm.polyglot.Context;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class KLScriptLoader {

    public static void load(ScriptPack pack, ScriptFileInfo info,
                            Map<String, List<MixinDSL>> mixinMap, CallbackInfo ci)  {
        String sourceCode = String.join("\n", info.lines);

        //根据文件后缀进行处理
        if (isTsFile(info.file)) {
            Debugger.out("正在处理 " + info.location + " 文件");
            //typescript擦除类型

            sourceCode = TsEraser.eraseTypes(sourceCode);
            Debugger.out("修改后的源代码(TS→ES6) " + info.location + ":\n" + sourceCode);
            //根据配置决定是否转换ES6语法
            if (ConfigManager.shouldUseModernJS()) {
                sourceCode = ModernJSParser.parse(sourceCode);
                Debugger.out("修改后的源代码(ES6→ES5) " + info.location + ":\n" + sourceCode);
            }
        } else if (isJsFile(info.file)) {
            //根据配置决定是否进行现代JS转换
            if (ConfigManager.shouldUseModernJS()) {
                sourceCode = ModernJSParser.parse(sourceCode);
                Debugger.out("修改后的源代码(ES5兼容运行) " + info.location + ":\n" + sourceCode);
            }
        }
        //先处理KLM
        if (mixinMap.getOrDefault(info.location,null)  != null ) {
            sourceCode = applyMixin(pack,info,mixinMap,sourceCode);
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
            //Debugger.out("修改后的源代码 " + info.location + ":\n" + modifiedSourceCode.get());
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

        if (GraalJSCompat.canUseGraalJS) {
            graalEvalString(pack, info, code);
        } else {
            pack.manager.context.evaluateString(
                    pack.scope,
                    code,
                    info.location,
                    1,
                    (Object)null
            );
        }
    }
    public static void graalEvalString(ScriptPack pack,ScriptFileInfo info, String code) {
        if (!GraalJSCompat.canUseGraalJS) {
            pack.manager.context.evaluateString(
                    pack.scope,
                    code,
                    info.location,
                    1,
                    (Object)null
            );
            return;
        } else {
            GraalApi.loadScript(pack, info, code);
        }
        

    }
    
    /**
     * Script metadata object exposed to JavaScript as __script__
     * Provides access to script file information
     */
    public static class ScriptMetadata {
        private final ScriptFileInfo info;
        private final ScriptPack pack;
        
        public ScriptMetadata(ScriptFileInfo info, ScriptPack pack) {
            this.info = info;
            this.pack = pack;
        }
        
        /** Get script file name (e.g., "e.js") */
        public String getName() {
            return info.file;
        }
        
        /** Get full script location (e.g., "server_scripts:e.js") */
        public String getLocation() {
            return info.location;
        }
        
        /** Get script type (e.g., "server", "client", "startup") */
        public String getType() {
            return pack.manager.scriptType.name;
        }
        
        /** Get script namespace (e.g., "kubejs", "server_scripts") */
        public String getNamespace() {
            return pack.info.namespace;
        }
        
        /** Get script directory path */
        public String getPath() {
            return info.file;
        }
        
        @Override
        public String toString() {
            return info.location;
        }
    }
}
