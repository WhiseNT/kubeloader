package com.whisent.kubeloader.graal.event;

import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.EventExit;
import dev.latvian.mods.kubejs.event.IEventHandler;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ListJS;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.util.concurrent.atomic.AtomicBoolean;

public class GraalEventHandlerProxy implements ProxyExecutable {
    private final EventHandler handler;
    private final Context capturedContext;
    private static final AtomicBoolean SCRIPT_TYPE_WARNED = new AtomicBoolean(false);

    public GraalEventHandlerProxy(EventHandler handler) {
        this.handler = handler;
        this.capturedContext = ScriptTypeThreadLocal.getCurrentContext();
        if (this.capturedContext == null) {
            System.err.println("[KubeLoader] WARNING: GraalEventHandlerProxy created without context!");
        }
    }

    @Override
    public Object execute(Value... arguments) {
        ScriptType type = getCurrentScriptType();
        if (type == null) {
            throw new IllegalStateException("Cannot determine script type for event handler");
        }
        try {
            if (arguments.length == 1) {
                Value handlerFunc = arguments[0];
                if (handlerFunc.canExecute()) {
                    IEventHandler adapted = createGraalHandler(handlerFunc, type);
                    handler.listen(type, null, adapted);
                }
            } else if (arguments.length == 2) {
                Object extraIdArg = convertGraalValueToJava(arguments[0]);
                Value handlerFunc = arguments[1];
                if (handlerFunc.canExecute()) {
                    IEventHandler adapted = createGraalHandler(handlerFunc, type);
                    for (Object extraId : ListJS.orSelf(extraIdArg)) {
                        handler.listen(type, extraId, adapted);
                    }
                }
            } else {
                throw new IllegalArgumentException("Invalid number of arguments: " + arguments.length);
            }
        } catch (Exception ex) {
            type.console.error("Error registering GraalJS event handler: " + ex.getMessage());
            ex.printStackTrace();
        }
        return null;
    }
    
    private IEventHandler createGraalHandler(Value graalFunction, ScriptType type) {
        return event -> {
            if (capturedContext == null) {
                type.console.error("No GraalJS context available for event handler");
                return null;
            }
            try {
                capturedContext.enter();
                try {
                    Value result = graalFunction.execute(event);

                    if (result != null && !result.isNull()) {
                        if (result.isHostObject()) {
                            Object hostObject = result.asHostObject();
                            if (hostObject instanceof dev.latvian.mods.kubejs.event.EventResult) {
                                return hostObject;
                            }
                        }
                        if (result.isBoolean()) return result.asBoolean();
                        if (result.isString()) {
                            switch (result.asString().toLowerCase()) {
                                case "pass": case "true": return true;
                                case "cancel": case "interrupt": case "stop": case "false": return false;
                            }
                        }
                        if (result.isHostObject()) return result.asHostObject();
                        return null;
                    }
                    return null;
                } finally {
                    capturedContext.leave();
                }
            } catch (Exception e) {
                EventExit eventExit = extractEventExit(e);
                if (eventExit != null) {
                    throw eventExit;
                }
                type.console.error("Exception in GraalJS event handler: " + e.getClass().getName() + ": " + e.getMessage());
                return null;
            }
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

    private static Object convertGraalValueToJava(Value value) {
        if (value == null || value.isNull()) return null;
        if (value.isHostObject()) return value.asHostObject();
        if (value.isString()) return value.asString();
        if (value.isNumber()) {
            if (value.fitsInInt()) return value.asInt();
            if (value.fitsInLong()) return value.asLong();
            return value.asDouble();
        }
        if (value.isBoolean()) return value.asBoolean();
        if (value.hasArrayElements()) {
            long size = value.getArraySize();
            Object[] array = new Object[(int) size];
            for (int i = 0; i < size; i++) array[i] = convertGraalValueToJava(value.getArrayElement(i));
            return array;
        }
        return value;
    }

    private ScriptType getCurrentScriptType() {
        ScriptType type = ScriptTypeThreadLocal.get();
        if (type != null) return type;
        if (SCRIPT_TYPE_WARNED.compareAndSet(false, true)) {
            System.err.println("[KubeLoader] WARNING: ScriptTypeThreadLocal not set, using STARTUP");
        }
        return ScriptType.STARTUP;
    }

    public static class ScriptTypeThreadLocal {
        private static final ThreadLocal<ScriptType> CURRENT_TYPE = new ThreadLocal<>();
        private static final ThreadLocal<Context> CURRENT_CONTEXT = new ThreadLocal<>();

        public static void set(ScriptType type, Context context) {
            CURRENT_TYPE.set(type);
            CURRENT_CONTEXT.set(context);
        }

        public static ScriptType get() { return CURRENT_TYPE.get(); }
        public static Context getCurrentContext() { return CURRENT_CONTEXT.get(); }

        public static void clear() {
            CURRENT_TYPE.remove();
            CURRENT_CONTEXT.remove();
        }
    }
}
