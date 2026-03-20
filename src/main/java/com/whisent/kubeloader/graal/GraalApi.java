package com.whisent.kubeloader.graal;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.compat.GraalJSCompat;
import com.whisent.kubeloader.graal.event.GraalEventGroupProxy;
import com.whisent.kubeloader.graal.event.GraalEventHandlerProxy;
import com.whisent.kubeloader.graal.wrapper.CustomHostAccess;
import com.whisent.kubeloader.graal.wrapper.WrapperHelper;
import com.whisent.kubeloader.impl.mixin.GraalPack;
import com.whisent.kubeloader.scripts.KLScriptLoader;
import com.whisent.kubeloader.utils.Debugger;
import com.whisent.kubeloader.impl.mixin.ScriptManagerInterface;
import dev.latvian.mods.kubejs.event.*;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ConsoleJS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.*;

import java.io.IOException;

public class GraalApi {

    private static final Logger LOGGER = Kubeloader.LOGGER;
    private static boolean wrappersInitialized = false;

    /**
     * 向指定 Context 注册绑定
     * 
     * 绑定策略：
     * 1. EventGroupWrapper → GraalEventGroupProxy 包装
     * 2. Class<?> → StaticClassProxy 代理（无需 @HostAccess.Export）
     * 3. 普通对象 → RhinoAnnotationWrapper 自动处理注解 + 直接绑定
     * 
     * 注意：console 等特殊对象应在外部单独处理（如 ScriptPackMixin）
     */
    public static void registerBinding(Context context, String name, Object value) {
        // 检查依赖可用性
        if (!GraalJSCompat.canUseGraalJS) {
            return;
        }
        // 1. 特殊处理 EventGroupWrapper
        if (value instanceof EventGroupWrapper eventGroupWrapper) {
            try {
                GraalEventGroupProxy proxy = new GraalEventGroupProxy(eventGroupWrapper);
                context.getBindings("js").putMember(name, proxy);
                Debugger.out("[KubeLoader] ✓ Wrapped as EventGroupWrapper proxy: " + name);
                return;
            } catch (Exception e) {
                Debugger.out("[KubeLoader] ✗ Failed to wrap EventGroupWrapper: " + name);
                e.printStackTrace();
                // Fallback to direct binding
                context.getBindings("js").putMember(name, value);
                return;
            }
        }
        
        // 2. 处理 Class<?> 类型 - 创建静态方法代理（无需注解）
        if (value instanceof Class<?> clazz) {
            try {
                StaticClassProxy proxy = new StaticClassProxy(clazz);
                context.getBindings("js").putMember(name, proxy);
                Debugger.out("[KubeLoader] ✓ Bound as static class proxy: " + name + " (" + clazz.getSimpleName() + ")");
                return;
            } catch (Exception e) {
                Debugger.out("[KubeLoader] ✗ Failed to create static class proxy for " + name + ", using fallback");
                e.printStackTrace();
                // Fallback: try Java.type() approach (requires @HostAccess.Export)
                registerClassViaJavaType(context, name, clazz);
                return;
            }
        }
        
        // 3. 普通对象 - 使用 RhinoAnnotationWrapper 自动处理
        //    如果对象有 Rhino 注解，RhinoAnnotationWrapper.wrap() 会返回 ProxyObject
        //    如果没有注解，会返回原对象，GraalJS 可以直接访问
        Object wrapped = com.whisent.kubeloader.graal.wrapper.RhinoAnnotationWrapper.wrap(value);
        context.getBindings("js").putMember(name, wrapped);
        Debugger.out("[KubeLoader] ✓ Bound object (RhinoAnnotationWrapper processed): " + name);
    }
    
    /**
     * Fallback: 通过 Java.type() 绑定类（需要 @HostAccess.Export）
     */
    private static void registerClassViaJavaType(Context context, String name, Class<?> clazz) {
        String className = clazz.getName();
        
        Value javaObj = context.getBindings("js").getMember("Java");
        if (javaObj != null && !javaObj.isNull()) {
            Value typeFunc = javaObj.getMember("type");
            if (typeFunc != null && typeFunc.canExecute()) {
                Value classRef = typeFunc.execute(className);
                context.getBindings("js").putMember(name, classRef);
                System.out.println("[KubeLoader] ⚠ Bound via Java.type (requires @HostAccess.Export): " + name);
                return;
            }
        }
        
        // Final fallback
        Value classRef = context.asValue(clazz);
        context.getBindings("js").putMember(name, classRef);
        Debugger.out("[KubeLoader] ⚠ Bound via asValue (requires @HostAccess.Export): " + name);
    }


    public static void eval(Context context, String script, ScriptFileInfo info, ScriptPack pack) {
        String filePath = info.location;
        ScriptType type = pack.manager.scriptType;
        if (context == null || script == null) return;

        // 检查依赖可用性
        if (!GraalJSCompat.canUseGraalJS()) {
            LOGGER.warn("[KubeLoader] 尝试执行脚本但 GraalJS 不可用: {}", filePath);
            return;
        }

        try {
            Source source = Source.newBuilder("js", script, filePath).build();
            // Note: Script location is already set in KLScriptLoader via DynamicGraalConsole.setCurrentLocation()
            context.eval(source);
        } catch (PolyglotException e) {
            // 安全获取行号信息
            String linePart = "";
            if (e.getSourceLocation() != null) {
                linePart = "#" + e.getSourceLocation().getStartLine();
            }
            
            String errorMessage = filePath + linePart + ": " + e.getMessage();
            
            if (type == null) {
                System.err.println("[GraalJS] " + errorMessage);
                e.printStackTrace();
                return;
            }
            
            if (type == ScriptType.STARTUP) {
                ConsoleJS.STARTUP.error(errorMessage);
                ConsoleJS.STARTUP.error(e);
            } else if (type == ScriptType.SERVER) {
                ConsoleJS.SERVER.error(errorMessage);
                ConsoleJS.SERVER.error(e);
            } else if (type == ScriptType.CLIENT) {
                ConsoleJS.CLIENT.error(errorMessage);
                ConsoleJS.CLIENT.error(e);
            } else {
                System.err.println("[GraalJS] " + errorMessage);
                e.printStackTrace();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void loadScript(ScriptPack pack, ScriptFileInfo info, String code) {
        GraalPack graalPack = (GraalPack) pack;
        Context context = (Context) graalPack.kubeLoader$getGraalContext();
    
        if (context == null) {
            LOGGER.error("[KubeLoader] GraalJS Context is null for pack: {}", pack.info.namespace);
            return;
        }
    
        var bindings = context.getBindings("js");
    
        // 注入脚本元数据
        KLScriptLoader.ScriptMetadata scriptMetadata = new KLScriptLoader.ScriptMetadata(info, pack);
        bindings.putMember("__script__", scriptMetadata);
        DynamicGraalConsole fileConsole = new DynamicGraalConsole(
            pack.manager.scriptType.console,
            info.location
        );
        bindings.putMember("__kubeLoaderNativeConsole", fileConsole);
    
        try {
            GraalEventHandlerProxy.ScriptTypeThreadLocal.set(pack.manager.scriptType, context);
        context.eval("js", """
            (function() {
                let nativeConsole = __kubeLoaderNativeConsole
                function createLogger(level) {
                    return function(...args) {
                        try {
                            throw new Error()
                        } catch (e) {
                            let stackLines = e.stack.split('\\n')
                            let callerLine = stackLines[2] || ''
                            let match = callerLine.match(/(.+):(\\d+):\\d+/)
                            let filePath = match ? match[1].trim() : 'unknown'
                            filePath = filePath.replace(/^at\\s+/, '')
                            filePath = filePath.replace(/[()]/g, '')
                            if (filePath.includes(' ')) {
                                filePath = filePath.split(' ').pop()
                            }
                            let lineNumber = match ? match[2] : '?'
                            nativeConsole['__' + level](filePath, String(lineNumber), args)
                        }
                    }
                }
                globalThis.console = {
                    log: createLogger('log'),
                    info: createLogger('info'),
                    warn: createLogger('warn'),
                    error: createLogger('error'),
                    debug: createLogger('debug')
                }
            })()
        """);
            GraalApi.eval(context, code, info, pack);
            
        } finally {
            GraalEventHandlerProxy.ScriptTypeThreadLocal.clear();
        }
    }


    /**
     * 创建一个预配置的 GraalJS Context
     */
    public static Context createContext() {
        // 检查依赖可用性
        System.out.println("[KubeLoader] GraalJS检查: canUseGraalJS=" + GraalJSCompat.canUseGraalJS);
        if (!GraalJSCompat.canUseGraalJS) {
            LOGGER.error("[KubeLoader] 无法创建 GraalJS Context：依赖不可用");
            return null;
        }

        // Initialize type wrappers on first use (fallback for standalone usage)
        // Note: TypeWrappers will be automatically synced via TypeWrappersMixin when KubeJS registers them
        if (!wrappersInitialized) {
            wrappersInitialized = true;
        }

        // Use custom HostAccess that applies type wrappers automatically
        Context ctx = Context.newBuilder("js")
                .allowHostAccess(CustomHostAccess.create())
                .allowHostClassLookup(s -> true)
                .allowNativeAccess(true)
                .allowAllAccess(true)
                .build();

        Engine engine = ctx.getEngine();
        if (engine != null) {
            System.out.println("[KubeLoader] GraalJS Engine:" + engine.getImplementationName() + " " + engine.getVersion());
            System.out.println("[KubeLoader] GraalJS Engine options: " + engine.getOptions());
        }
        // 直接在 JS 中定义 loadClass = Java.type
        ctx.eval("js", """
            Java.loadClass = Java.type
            Java.class = Java.type("java.lang.Class").forName("java.lang.Class")
            Java.class.forName = Java.type("java.lang.Class").forName
        """);
        // Register WrapperHelper for advanced usage
        WrapperHelper.registerInContext(ctx);

        // Load wrapper compatibility layer
        loadWrapperCompatLayer(ctx);


        System.out.println("[KubeLoader] Context created with automatic object wrapping enabled");
        // Note: HostAccess checking removed due to version compatibility issues
        return ctx;
    }

    public static Context createContext(ScriptManager manager) {
        ScriptManagerInterface thiz = (ScriptManagerInterface) manager;
        Context ctx = createContext();

        if (ctx == null) {
            LOGGER.error("[KubeLoader] Failed to create GraalJS context for manager: {}", manager);
            return null;
        }

//        registerBinding(ctx,"ForgeEvents",new ForgeEventWrapper("ForgeEvents", MinecraftForge.EVENT_BUS));

        // 安全地遍历绑定，过滤掉null值
        thiz.getKubeLoader$bindings().entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getValue() != null)
            .forEach(entry -> {
                try {
                    registerBinding(ctx, entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    LOGGER.error("[KubeLoader] Failed to register binding '{}': {}", entry.getKey(), e.getMessage());
                }
            });

        return ctx;
    }

    /**
     * Load the wrapper compatibility layer script into the context
     */
    private static void loadWrapperCompatLayer(Context ctx) {
        try {
            var stream = GraalApi.class.getResourceAsStream("/graaljs/wrapper_compat.js");
            if (stream != null) {
                String script = new String(stream.readAllBytes());
                ctx.eval("js", script);
            } else {
                System.err.println("[KubeLoader] Warning: wrapper_compat.js not found");
            }
        } catch (Exception e) {
            System.err.println("[KubeLoader] Failed to load wrapper compatibility layer: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public static IEventHandler createGraalHandler(Object handler, ScriptType type) {
        return event -> {
            try {
                if (handler instanceof Value graalFunction && graalFunction.canExecute()) {
                    // Execute GraalJS function with event parameter
                    Value result = graalFunction.execute(event);

                    // Process the result properly for EventResult handling
                    if (result != null && !result.isNull()) {
                        // Handle EventResult returns
                        if (result.isHostObject()) {
                            Object hostObject = result.asHostObject();
                            if (hostObject instanceof dev.latvian.mods.kubejs.event.EventResult) {
                                return hostObject;
                            }
                        }

                        // Handle boolean returns (common pattern in JS)
                        if (result.isBoolean()) {
                            boolean boolValue = result.asBoolean();
                            // For boolean, true means pass, false means interrupt
                            // We need to create appropriate EventResult, but since we don't have constants,
                            // we'll return the boolean and let the caller handle it
                            return boolValue;
                        }

                        // Handle string returns that might represent event results
                        if (result.isString()) {
                            String strValue = result.asString();
                            switch (strValue.toLowerCase()) {
                                case "pass":
                                case "true":
                                    return true; // Pass
                                case "false":
                                case "cancel":
                                case "interrupt":
                                case "stop":
                                    return false; // Interrupt
                            }
                        }

                        // Default: return the unwrapped host object if available
                        if (result.isHostObject()) {
                            return result.asHostObject();
                        }

                        // Return null for unsupported types (will be treated as PASS)
                        return null;
                    }

                    // Null result means PASS
                    return null;
                }
            } catch (Exception e) {
                type.console.error("Error in GraalJS event handler", e);
            }
            return null;
        };
    }
    public static void throwException(Throwable ex, EventExceptionHandler exh,EventJS event,EventHandlerContainer itr ) throws EventExit {
        var throwable = ex;


        // 处理GraalJS的异常包装
        while (throwable instanceof PolyglotException e) {
            if (e.isGuestException() && e.getCause() != null) {
                throwable = e.getCause();
            } else if (e.isInternalError() && e.getCause() != null) {
                throwable = e.getCause();
            } else {
                break;
            }
        }
        if (throwable instanceof EventExit exit) {
            throw exit;
        }

        if (exh == null || (throwable = exh.handle(event, itr, throwable)) != null) {
            throw EventResult.Type.ERROR.exit(throwable);
        }
    }
    public static void setGraalContext(GraalPack pack,Object context) {
        if (context == null) {
            LOGGER.error("[KubeLoader] Attempt to set null GraalJS context");
            return;
        }
        Context ctx = (Context) context;
        ctx.getBindings("js").putMember("console",((ScriptPack)pack).manager.scriptType.console);
    }
}
