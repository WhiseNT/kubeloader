package com.whisent.kubeloader.graal;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.compat.GraalJSCompat;
import com.whisent.kubeloader.graal.event.GraalEventGroupProxy;
import com.whisent.kubeloader.graal.event.GraalEventHandlerProxy;
import com.whisent.kubeloader.graal.event.GraalEventSignal;
import com.whisent.kubeloader.graal.wrapper.GraalTypeWrappers;

import com.whisent.kubeloader.impl.mixin.GraalPack;
import com.whisent.kubeloader.impl.mixin.ScriptManagerInterface;
import com.whisent.kubeloader.scripts.KLScriptLoader;
import com.whisent.kubeloader.utils.Debugger;
import dev.latvian.mods.kubejs.event.*;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ConsoleJS;

import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.*;

import java.io.IOException;

// 我好像不小心把我之前实现的行数显示搞坏了, 额Whise你可以先修一下, 我没空我还要去整其他的
public class GraalApi {
    private static final Logger LOGGER = Kubeloader.LOGGER;
    private static volatile HostAccess CACHED_HOST_ACCESS;
    private static final Object HOST_ACCESS_LOCK = new Object();
    
    private static HostAccess getHostAccess() {
        if (CACHED_HOST_ACCESS == null) {
            synchronized (HOST_ACCESS_LOCK) {
                if (CACHED_HOST_ACCESS == null) {
                    try {
                        HostAccess.Builder builder = HostAccess.newBuilder(HostAccess.ALL);
                        GraalTypeWrappers.registerTargetTypeMappings(builder);
                        CACHED_HOST_ACCESS = builder.build();
                    } catch (Exception e) {
                        LOGGER.error("无法构建自定义HostAccess, 退回HostAccess.ALL", e);
                        CACHED_HOST_ACCESS = HostAccess.ALL;
                    }
                }
            }
        }
        return CACHED_HOST_ACCESS;
    }
    
    public static void invalidateHostAccess() {
        synchronized (HOST_ACCESS_LOCK) {
            CACHED_HOST_ACCESS = null;
        }
        GraalTypeWrappers.reload();
    }

    public static void registerBinding(Context context, String name, Object value) {
        if (!GraalJSCompat.canUseGraalJS) return;
        if (value instanceof EventGroupWrapper eventGroupWrapper) {
            try {
                GraalEventGroupProxy proxy = new GraalEventGroupProxy(eventGroupWrapper);
                context.getBindings("js").putMember(name, proxy);
            } catch (Exception e) {
                Debugger.out("[KubeLoader] Failed to wrap EventGroupWrapper: " + name);
                e.printStackTrace();
                context.getBindings("js").putMember(name, value);
            }
            return;
        }
        if (value instanceof Class<?> clazz) {
            registerClassViaJavaType(context, name, clazz);
            return;
        }
        context.getBindings("js").putMember(name, value);
        Debugger.out("[KubeLoader] Bound object directly: " + name);
    }
    
    private static void registerClassViaJavaType(Context context, String name, Class<?> clazz) {
        String className = clazz.getName();
        try {
            Value javaObj = context.getBindings("js").getMember("Java");
            if (javaObj != null && !javaObj.isNull()) {
                Value typeFunc = javaObj.getMember("type");
                if (typeFunc != null && typeFunc.canExecute()) {
                    Value classRef = typeFunc.execute(className);
                    context.getBindings("js").putMember(name, classRef);
                    Debugger.out("[KubeLoader] Bound via Java.type: " + name);
                    return;
                }
            }
        } catch (Exception e) {
            Debugger.out("[KubeLoader] Java.type failed for " + name + ", using asValue fallback");
        }
        Value classRef = context.asValue(clazz);
        context.getBindings("js").putMember(name, classRef);
        Debugger.out("[KubeLoader] Bound via asValue: " + name);
    }

    public static void eval(Context context, String script, ScriptFileInfo info, ScriptPack pack) {
        String filePath = info.location;
        ScriptType type = pack.manager.scriptType;
        if (context == null || script == null) return;

        if (!GraalJSCompat.canUseGraalJS()) {
            LOGGER.warn("尝试执行脚本但 GraalJS 不可用: {}", filePath);
            return;
        }

        try {
            Source source = Source.newBuilder("js", script, filePath).build();
            context.eval(source);
        } catch (PolyglotException e) {
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
                    function parseStack(stackStr) {
                        if (!stackStr) return null
                        let lines = stackStr.split(/\\r?\\n/)
                        if (lines.length <= 1) {
                            lines = stackStr.split(String.fromCharCode(10))
                        }
                        if (lines.length <= 1) {
                            lines = stackStr.split(String.fromCharCode(13))
                        }
                        let callerLine = lines.length > 2 ? lines[2] : (lines.length > 1 ? lines[1] : '')
                        if (!callerLine) return null
                        callerLine = callerLine.trim()
                        callerLine = callerLine.replace(/^at\\s+/, '')
                        callerLine = callerLine.replace(/<\\w+>\\s*/g, '')
                        callerLine = callerLine.replace(/[()]/g, '')
                        callerLine = callerLine.trim()
                        // 贪婪匹配: 从右侧解析 path:line:column
                        let match = callerLine.match(/^(.+):(\\d+):(\\d+)$/)
                        if (!match) {
                            match = callerLine.match(/^(.+):(\\d+)$/)
                        }
                        return match
                    }
                    function createLogger(level) {
                        return function(...args) {
                            try {
                                throw new Error()
                            } catch (e) {
                                let match = parseStack(e.stack)
                                let filePath = match ? match[1].trim() : 'unknown'
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
    
    public static Context createContext() {
        Engine sharedEngine = Engine.newBuilder()
            .allowExperimentalOptions(true)
            .option("js.ecmascript-version", "2024")
            .option("js.nashorn-compat", "true")
            .option("js.foreign-object-prototype", "true")
            .build();

        System.out.println("[KubeLoader] GraalJS检查: canUseGraalJS=" + GraalJSCompat.canUseGraalJS);
        if (!GraalJSCompat.canUseGraalJS) {
            LOGGER.error("[KubeLoader] 无法创建 GraalJS Context：依赖不可用");
            return null;
        }
        
        HostAccess hostAccess = getHostAccess();

        Context ctx = Context.newBuilder("js")
            .engine(sharedEngine)
            .allowAllAccess(true)
            .allowHostAccess(hostAccess)
            .allowHostClassLookup(s -> true)
            .allowNativeAccess(true)
            .allowExperimentalOptions(true)
            .build();
        ctx.eval("js", """
            Java.loadClass = Java.type
            Java.class = Java.type("java.lang.Class").forName("java.lang.Class")
            Java.class.forName = Java.type("java.lang.Class").forName
        """);
        
//        WrapperHelper.registerInContext(ctx);
        return ctx;
    }

    public static Context createContext(ScriptManager manager) {
        ScriptManagerInterface thiz = (ScriptManagerInterface) manager;
        Context ctx = createContext();

        if (ctx == null) {
            LOGGER.error("[KubeLoader] Failed to create GraalJS context for manager: {}", manager);
            return null;
        }

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
    
    public static IEventHandler createGraalHandler(Object handler, ScriptType type) {
        return event -> {
            try {
                if (handler instanceof Value graalFunction && graalFunction.canExecute()) {
                    Value result = graalFunction.execute(event);

                    if (result != null && !result.isNull()) {
                        if (result.isHostObject()) {
                            Object hostObject = result.asHostObject();
                            if (hostObject instanceof dev.latvian.mods.kubejs.event.EventResult) {
                                return hostObject;
                            }
                        }
                        if (result.isBoolean()) {
                            return result.asBoolean();
                        }
                        if (result.isString()) {
                            String strValue = result.asString();
                            switch (strValue.toLowerCase()) {
                                case "pass": case "true": return true;
                                case "false": case "cancel": case "interrupt": case "stop": return false;
                            }
                        }
                        if (result.isHostObject()) {
                            return result.asHostObject();
                        }
                        return null;
                    }
                    return null;
                }
            } catch (Exception e) {
                EventExit eventExit = extractEventExit(e);
                if (eventExit != null) {
                    throw eventExit;
                }
                type.console.error("Error in GraalJS event handler", e);
            }
            return null;
        };
    }

    private static EventExit extractEventExit(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof EventExit exit) {
                return exit;
            }
            if (current instanceof GraalEventSignal signal) {
                return signal.toEventExit();
            }
            if (current instanceof PolyglotException polyglot && polyglot.isHostException()) {
                Throwable hostException = polyglot.asHostException();
                if (hostException != null && hostException != current) {
                    current = hostException;
                    continue;
                }
            }

            Throwable cause = current.getCause();
            if (cause == null || cause == current) {
                break;
            }
            current = cause;
        }

        return null;
    }

    public static void throwException(Throwable ex, EventExceptionHandler exh, EventJS event, EventHandlerContainer itr) throws EventExit {
        var throwable = ex;

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

    public static void setGraalContext(GraalPack pack, Object context) {
        if (context == null) {
            LOGGER.error("[KubeLoader] Attempt to set null GraalJS context");
            return;
        }
        Context ctx = (Context) context;
        ctx.getBindings("js").putMember("console", ((ScriptPack) pack).manager.scriptType.console);
    }
}
