package com.whisent.kubeloader.scripts;

import com.whisent.kubeloader.ConfigManager;
import com.whisent.kubeloader.compat.GraalJSCompat;
import com.whisent.kubeloader.definition.meta.Engine;
import com.whisent.kubeloader.graal.GraalApi;
import com.whisent.kubeloader.impl.mixin.ScriptFileInfoInterface;
import com.whisent.kubeloader.klm.ast.AstToSourceConverter;
import com.whisent.kubeloader.klm.ast.JSInjector;
import com.whisent.kubeloader.klm.dsl.EventProbe;
import com.whisent.kubeloader.klm.dsl.MixinDSL;
import com.whisent.kubeloader.utils.Debugger;
import dev.latvian.mods.kubejs.script.KubeJSContext;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.rhino.Parser;
import dev.latvian.mods.rhino.ast.AstRoot;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class KLScriptLoader {

    public static void load(ScriptPack pack, ScriptFileInfo info, String[] lines,
                            Map<String, List<MixinDSL>> mixinMap, KubeJSContext cx, CallbackInfo ci)  {
        String sourceCode = String.join("\n", lines);

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
            sourceCode = applyMixin(cx, pack, info, mixinMap, sourceCode);
        }
        evalString(cx, pack, info, sourceCode);
        ci.cancel();
    }

    public static String applyMixin(KubeJSContext cx, ScriptPack pack,ScriptFileInfo info,
                                    Map<String, List<MixinDSL>> mixinMap,String sourceCode) {
        Parser parser = new Parser(cx);
        AstRoot root = parser.parse(sourceCode, info.file, 0);
        AstToSourceConverter converter = new AstToSourceConverter(sourceCode);
        AtomicReference<String> modifiedSourceCode = new AtomicReference<>(sourceCode);
        // 按优先级升序排序，优先级高的后注入（addChildToFront 会把后注入的放更前面）
        List<MixinDSL> sortedDsls = new ArrayList<>(mixinMap.get(info.location));
        sortedDsls.sort((a, b) -> Integer.compare(a.getPriority(), b.getPriority()));
        sortedDsls.forEach(dsl -> {
            if (Objects.equals(dsl.getType(), "EventSubscription")) {
                modifiedSourceCode.set(EventProbe.applyTo(modifiedSourceCode.get(), dsl));
            } else if (Objects.equals(dsl.getType(), "FunctionDeclaration")) {
                JSInjector.injectFromDSL(root, dsl);
            }
            //Debugger.out("修改后的源代码 " + info.location + ":\n" + modifiedSourceCode.get());
            pack.manager.scriptType.console.info("Apply mixin for " + info.location + " from " + dsl.getSourcePath());
        });
        // 统一替换所有占位符
        modifiedSourceCode.set(converter.convertAndFixAll(root, JSInjector.getPlaceholderMap()));
        return modifiedSourceCode.get();
    }

    public static boolean isTsFile(String file) {
        return file.endsWith(".ts");
    }
    public static boolean isJsFile(String file) {
        return file.endsWith(".js");
    }
    public static void evalString(KubeJSContext cx, ScriptPack pack,ScriptFileInfo info,String code) {
        // 获取脚本指定的引擎
        Engine scriptEngine = getScriptEngine(info);
        
        // 根据引擎类型选择执行方式
        if (scriptEngine == Engine.both) {
            // 在两个引擎中都加载
            System.out.println("[KubeLoader] Evaluating script with BOTH engines: " + info.location);
            
            // 先用 GraalJS 加载
            if (GraalJSCompat.canUseGraalJS()) {
                try {
                    System.out.println("[KubeLoader] Evaluating with GraalJS: " + info.location);
                    graalEvalString(pack, info, code);
                } catch (Exception e) {
                    System.out.println("[KubeLoader] GraalJS evaluation failed: " + e.getMessage());
                }
            } else {
                System.out.println("[KubeLoader] GraalJS not available, skipping GraalJS evaluation");
            }
            
            // 再用 Rhino 加载
            try {
                System.out.println("[KubeLoader] Evaluating with Rhino: " + info.location);
                cx.evaluateString(
                    cx.topLevelScope,
                        code,
                        info.location,
                        1,
                        (Object)null
                );
            } catch (Exception e) {
                System.out.println("[KubeLoader] Rhino evaluation failed: " + e.getMessage());
            }
        } else if (scriptEngine == Engine.graaljs && GraalJSCompat.canUseGraalJS()) {
            System.out.println("[KubeLoader] Evaluating script with GraalJS: " + info.location);
            graalEvalString(pack, info, code);
        } else if (scriptEngine == Engine.rhino) {
            System.out.println("[KubeLoader] Evaluating script with Rhino: " + info.location);
            cx.evaluateString(
                cx.topLevelScope,
                    code,
                    info.location,
                    1,
                    (Object)null
            );
        } else {
            // DEFAULT：根据系统配置决定
            if (GraalJSCompat.canUseGraalJS()) {
                System.out.println("[KubeLoader] Evaluating script with GraalJS (DEFAULT): " + info.location);
                graalEvalString(pack, info, code);
            } else {
                System.out.println("[KubeLoader] Evaluating script with Rhino (DEFAULT): " + info.location);
                cx.evaluateString(
                        cx.topLevelScope,
                        code,
                        info.location,
                        1,
                        (Object)null
                );
            }
        }
    }
    
    /**
     * 获取脚本指定的引擎
     * @param info 脚本文件信息
     * @return 引擎类型，如果未指定则返回DEFAULT
     */
    private static Engine getScriptEngine(ScriptFileInfo info) {
        if (info instanceof ScriptFileInfoInterface) {
            ScriptFileInfoInterface fileInfoInterface = (ScriptFileInfoInterface) info;
            return fileInfoInterface.kubeLoader$getEngine().orElse(Engine.default_engine);
        }
        return Engine.default_engine;
    }
    
    public static void graalEvalString(ScriptPack pack,ScriptFileInfo info, String code) {
        if (!GraalJSCompat.canUseGraalJS()) {
            var cx = (KubeJSContext) pack.manager.contextFactory.enter();
            cx.evaluateString(
                    cx.topLevelScope,
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
        
        /** Get script engine type */
        public String getEngine() {
            if (info instanceof ScriptFileInfoInterface) {
                ScriptFileInfoInterface fileInfoInterface = (ScriptFileInfoInterface) info;
                return fileInfoInterface.kubeLoader$getEngine()
                    .map(Engine::name)
                    .orElse("default_engine");
            }
            return "default_engine";
        }
        
        /** Check if script should be loaded in both engines */
        public boolean isBothEngines() {
            if (info instanceof ScriptFileInfoInterface) {
                ScriptFileInfoInterface fileInfoInterface = (ScriptFileInfoInterface) info;
                return fileInfoInterface.kubeLoader$getEngine()
                    .map(engine -> engine == Engine.both)
                    .orElse(false);
            }
            return false;
        }
        
        @Override
        public String toString() {
            return info.location;
        }
    }
}