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
        String originalSource = String.join("\n", lines);
        String sourceCode = originalSource;

        //根据文件后缀进行处理
        if (isTsFile(info.file)) {
            Debugger.out("正在处理 " + info.location + " 文件");
            //typescript擦除类型

            sourceCode = TsEraser.eraseTypes(sourceCode);
            Debugger.out("修改后的源代码(TS→ES6) " + info.location + ":\n" + sourceCode);
            //根据配置决定是否转换ES6语法
            if (ConfigManager.shouldUseModernJS()) {
                try {
                    sourceCode = ModernJSParser.parse(sourceCode, isRhinoTarget(info));
                } catch (ModernJSParseException e) {
                    skipScript(pack, info, e);
                    ci.cancel();
                    return;
                }
                Debugger.out("修改后的源代码(ES6→ES5) " + info.location + ":\n" + sourceCode);
            }
        } else if (isJsFile(info.file)) {
            //根据配置决定是否进行现代JS转换
            if (ConfigManager.shouldUseModernJS()) {
                try {
                    sourceCode = ModernJSParser.parse(sourceCode, isRhinoTarget(info));
                } catch (ModernJSParseException e) {
                    skipScript(pack, info, e);
                    ci.cancel();
                    return;
                }
                Debugger.out("修改后的源代码(ES5兼容运行) " + info.location + ":\n" + sourceCode);
            }
        }
        // 先处理KLM；随后用 Rhino 自己的 parser 解析一遍最终代码做语法校验。
        // 校验失败时把报错换算成「原始文件:行 + 原始代码」再报出来，
        // 避免 Rhino 给的“转换后行号”把用户引到源码里并不存在的那一行。
        try {
            if (mixinMap.getOrDefault(info.location,null)  != null ) {
                sourceCode = applyMixin(cx, pack, info, mixinMap, sourceCode);
            }
            new Parser(cx).parse(sourceCode, info.file, 0);
        } catch (dev.latvian.mods.rhino.RhinoException e) {
            int generatedLine = e.lineNumber() > 0 ? e.lineNumber() : 1;
            int originalLine = OriginalLineMapper.toOriginalLine(sourceCode, originalSource, generatedLine);
            // 原始代码里若是已知的「Rhino 解析不了」的语法，那才是真正的原因，
            // 比"转换器出问题"更可能，所以先把改写建议摆出来
            String unsupported = ModernJSSyntaxGuard.explainUnsupported(originalSource);
            String hint = unsupported != null
                    ? unsupported + "。若这一行并没有用到它，那多半是 ModernJS 转换器的问题，"
                    + "请把这一行连同原始写法反馈给作者"
                    : "多半是 ModernJS 转换器在这个写法上出了问题，请把这一行连同原始写法反馈给作者；临时可先改写绕过";
            skipScript(pack, info, new ModernJSParseException(
                    "转换/校验后代码不合法：" + e.getMessage() + "（转换后第 " + generatedLine + " 行）",
                    originalLine,
                    OriginalLineMapper.lineText(originalSource, originalLine),
                    hint));
            ci.cancel();
            return;
        }

        // 缺失内建（Promise/Proxy/.at/...）：Rhino 里没有，跑到那一行才抛 TypeError，
        // 而报错本身看不出该怎么改。这里提前把改写建议写进日志，但不拦脚本——
        // 这类代码可能写在不执行的分支里，硬失败反而会误伤。
        if (isRhinoTarget(info)) {
            logCompatWarnings(pack, info, originalSource);
        }

        evalString(cx, pack, info, sourceCode, originalSource);
        ci.cancel();
    }

    /**
     * 转换/扫描发现问题时的统一处理：日志给出「文件:行 + 原始代码 + 改写建议」，
     * 然后 <b>只跳过这一个脚本</b>，同包其它脚本照常加载。
     */
    private static void skipScript(ScriptPack pack, ScriptFileInfo info, ModernJSParseException e) {
        String message = "[KubeLoader] 已跳过脚本（不支持的语法）" + info.location + "：" + e.describe();
        pack.manager.scriptType.console.error(message);
        Debugger.out(message);
    }

    /**
     * 目标引擎里是否包含 Rhino。
     *
     * <p>{@code both} 会在两个引擎里都跑，而 {@code graaljs}/{@code default_engine}
     * 在拿不到 GraalJS 时会回退到 Rhino —— 取值与 {@link #evalString} 的分支保持一致，
     * 否则会出现「以为走 GraalJS 而放过，实际用 Rhino 跑出错误结果」。</p>
     */
    private static boolean isRhinoTarget(ScriptFileInfo info) {
        Engine engine = getScriptEngine(info);
        if (engine == Engine.both || engine == Engine.rhino) return true;
        return !GraalJSCompat.canUseGraalJS();
    }

    /**
     * 提示脚本里用到的「Rhino 没有的内建」。
     *
     * <p>只告警不拦截：同一段代码可能写在不会执行到的分支里，硬失败会误伤。
     * 扫描用的是<b>原始</b>源码，这样行号直接就是用户写的那一行。</p>
     */
    private static void logCompatWarnings(ScriptPack pack, ScriptFileInfo info, String originalSource) {
        List<String> warnings = ModernJSSyntaxGuard.collectWarnings(originalSource);
        if (warnings.isEmpty()) return;
        String message = "[KubeLoader] " + info.location + " 可能无法在 Rhino 下正常工作：\n  "
                + String.join("\n  ", warnings);
        pack.manager.scriptType.console.warn(message);
        Debugger.out(message);
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
        evalString(cx, pack, info, code, null);
    }

    /**
     * 执行脚本。
     *
     * <p>{@code originalSource} 是转换前的原文（可为 null），用来把报错行号映射回
     * 用户真正写的那一行。每个分支都单独兜住异常：一个脚本失败只影响它自己，
     * 同包其它脚本照常加载。</p>
     */
    public static void evalString(KubeJSContext cx, ScriptPack pack, ScriptFileInfo info,
                                  String code, String originalSource) {
        // 获取脚本指定的引擎
        Engine scriptEngine = getScriptEngine(info);

        // 根据引擎类型选择执行方式
        if (scriptEngine == Engine.both) {
            // 在两个引擎中都加载
            System.out.println("[KubeLoader] Evaluating script with BOTH engines: " + info.location);

            // 先用 GraalJS 加载
            if (GraalJSCompat.canUseGraalJS()) {
                System.out.println("[KubeLoader] Evaluating with GraalJS: " + info.location);
                evalSafely(info, code, originalSource, "GraalJS", () -> graalEvalString(pack, info, code));
            } else {
                System.out.println("[KubeLoader] GraalJS not available, skipping GraalJS evaluation");
            }

            // 再用 Rhino 加载
            System.out.println("[KubeLoader] Evaluating with Rhino: " + info.location);
            evalSafely(info, code, originalSource, "Rhino",
                    () -> cx.evaluateString(cx.topLevelScope, code, info.location, 1, (Object) null));
        } else if (scriptEngine == Engine.graaljs && GraalJSCompat.canUseGraalJS()) {
            System.out.println("[KubeLoader] Evaluating script with GraalJS: " + info.location);
            evalSafely(info, code, originalSource, "GraalJS", () -> graalEvalString(pack, info, code));
        } else if (scriptEngine == Engine.rhino) {
            System.out.println("[KubeLoader] Evaluating script with Rhino: " + info.location);
            evalSafely(info, code, originalSource, "Rhino",
                    () -> cx.evaluateString(cx.topLevelScope, code, info.location, 1, (Object) null));
        } else {
            // DEFAULT：根据系统配置决定
            if (GraalJSCompat.canUseGraalJS()) {
                System.out.println("[KubeLoader] Evaluating script with GraalJS (DEFAULT): " + info.location);
                evalSafely(info, code, originalSource, "GraalJS", () -> graalEvalString(pack, info, code));
            } else {
                System.out.println("[KubeLoader] Evaluating script with Rhino (DEFAULT): " + info.location);
                evalSafely(info, code, originalSource, "Rhino",
                        () -> cx.evaluateString(cx.topLevelScope, code, info.location, 1, (Object) null));
            }
        }
    }

    /**
     * 跑一个引擎并兜住异常：失败只记录日志、不往外抛（一个脚本炸了不该影响别的脚本）。
     * 若报错来自 Rhino 且拿得到原始源码，就把行号换算成「原始第 N 行 + 该行原文」。
     */
    private static void evalSafely(ScriptFileInfo info, String code, String originalSource,
                                   String engineName, Runnable action) {
        try {
            action.run();
        } catch (Throwable e) {
            StringBuilder message = new StringBuilder("[KubeLoader] ")
                    .append(engineName).append(" 执行失败，已跳过该脚本：").append(info.location);
            if (e instanceof dev.latvian.mods.rhino.RhinoException re && re.lineNumber() > 0) {
                message.append("（转换后第 ").append(re.lineNumber()).append(" 行");
                if (originalSource != null) {
                    int originalLine = OriginalLineMapper.toOriginalLine(code, originalSource, re.lineNumber());
                    message.append("，原始第 ").append(originalLine).append(" 行")
                            .append("：").append(OriginalLineMapper.lineText(originalSource, originalLine));
                }
                message.append("）");
            }
            message.append(" -> ").append(e);
            System.out.println(message);
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