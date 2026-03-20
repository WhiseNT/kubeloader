 package com.whisent.kubeloader.graal.event;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import com.whisent.kubeloader.graal.wrapper.WrapperHelper;

import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.IEventHandler;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ListJS;
import dev.latvian.mods.rhino.util.RemapForJS;
import dev.latvian.mods.rhino.util.RemapPrefixForJS;

 /**
 * Proxy for EventHandler to make it callable from GraalJS.
 * 
 * This wraps KubeJS's EventHandler (which extends Rhino's BaseFunction)
 * and makes it executable in GraalJS by implementing ProxyExecutable.
 * 
 * Usage in GraalJS:
 * StartupEvents.init(event => {
 *     console.log("Event fired!");
 * });
 */
public class GraalEventHandlerProxy implements ProxyExecutable {
    private final EventHandler handler;
    private final Context capturedContext; // Capture GraalJS context at registration time
    private static final Map<Class<?>, EventProxyMetadata> EVENT_PROXY_METADATA_CACHE = new ConcurrentHashMap<>();
    private static final Object CACHE_MISS = new Object();
    private static final AtomicBoolean SCRIPT_TYPE_WARNED = new AtomicBoolean(false);
    
    public GraalEventHandlerProxy(EventHandler handler) {
        this.handler = handler;
        // CRITICAL: Capture the context when event is registered (during script loading)
        this.capturedContext = ScriptTypeThreadLocal.getCurrentContext();
        
        if (this.capturedContext == null) {
            System.err.println("[KubeLoader] WARNING: GraalEventHandlerProxy created without context!");
        }
    }
    
    @Override
    public Object execute(Value... arguments) {
        // Get ScriptType from the current context
        // This is a bit tricky - we need to get it from thread-local or context
        ScriptType type = getCurrentScriptType();
        
        if (type == null) {
            throw new IllegalStateException("Cannot determine script type for event handler");
        }
        
        try {
            if (arguments.length == 1) {
                // Single argument: handler function
                // StartupEvents.init(event => {...})
                Value handlerFunc = arguments[0];
                
                if (handlerFunc.canExecute()) {
                    IEventHandler adapted = createGraalHandler(handlerFunc, type);
                    handler.listen(type, null, adapted);
                }
            } else if (arguments.length == 2) {
                // Two arguments: extraId + handler function
                // ItemEvents.rightClicked('minecraft:diamond', event => {...})
                Object extraIdArg = convertGraalValueToJava(arguments[0]);
                Value handlerFunc = arguments[1];
                
                if (handlerFunc.canExecute()) {
                    IEventHandler adapted = createGraalHandler(handlerFunc, type);
                    
                    // Handle multiple extraIds (like KubeJS's ListJS.orSelf)
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
    
    /**
     * Create an IEventHandler that executes a GraalJS function
     * CRITICAL: This handler will be called from EventHandlerContainer.handle()
     * which is in Rhino context, but we need to execute in GraalJS context
     */
    private IEventHandler createGraalHandler(Value graalFunction, ScriptType type) {
        return event -> {
            if (capturedContext == null) {
                type.console.error("No GraalJS context available for event handler");
                return null;
            }
            
//            type.console.info("========== GraalJS Event Handler START ==========");
//            type.console.info("Event class: " + event.getClass().getName());
//            type.console.info("Captured context: " + capturedContext);
            
            try {
                // CRITICAL: Enter the captured context to make it active on current thread
                capturedContext.enter();
                
                try {
                    // Wrap event with intelligent proxy that maps private fields to getters
                    ProxyObject eventProxy = createEventProxy(event, capturedContext);
                    
                    // Execute the GraalJS function with the proxy
                    Value result = graalFunction.execute(eventProxy);
                    
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
                    
                } finally {
                    // CRITICAL: Always leave the context
                    capturedContext.leave();
                }
                
            } catch (Exception e) {
                type.console.error("Exception in GraalJS event handler: " + e.getClass().getName() + ": " + e.getMessage());
                return null;
            }
        };
    }
    
    /**
     * Convert GraalJS Value to Java object.
     * This tries to use KubeJS type wrappers when available.
     */
    private Object convertGraalValueToJava(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        
        // Host objects (already Java objects)
        if (value.isHostObject()) {
            return value.asHostObject();
        }
        
        // Strings - try to use KubeJS UtilsJS for common conversions
        if (value.isString()) {
            String strValue = value.asString();
            // Return raw string - KubeJS TypeWrappers will handle conversion
            // via CustomHostAccess if needed
            return strValue;
        }
        
        // Numbers
        if (value.isNumber()) {
            if (value.fitsInInt()) {
                return value.asInt();
            } else if (value.fitsInLong()) {
                return value.asLong();
            } else if (value.fitsInDouble()) {
                return value.asDouble();
            }
        }
        
        // Booleans
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        
        // Arrays
        if (value.hasArrayElements()) {
            long size = value.getArraySize();
            Object[] array = new Object[(int) size];
            for (int i = 0; i < size; i++) {
                array[i] = convertGraalValueToJava(value.getArrayElement(i));
            }
            return array;
        }
        
        // Default: return the Value itself and let GraalJS handle it
        return value;
    }
    
    /**
     * Get the current ScriptType from thread-local storage
     * This needs to be set by ScriptManagerMixin or similar
     */
    private ScriptType getCurrentScriptType() {
        // Try to get from thread-local (set by ScriptManagerMixin)
        ScriptType type = ScriptTypeThreadLocal.get();
        
        if (type != null) {
            return type;
        }
        
        if (SCRIPT_TYPE_WARNED.compareAndSet(false, true)) {
        }
        return ScriptType.STARTUP;
    }
    
    /**
     * Create an intelligent proxy for EventJS that maps private fields to their getter methods.
     * 
     * This solves the problem that ItemClickedEventJS has a private "player" field
     * but a public "getEntity()" method. In Rhino, accessing "player" automatically
     * calls the getter, but in GraalJS we need to do this explicitly.
     * 
     * Mapping strategy:
     * - "player" -> getEntity()
     * - "hand" -> getHand()
     * - "item" -> getItem()
     * - "target" -> getTarget()
     * - "entity" -> getEntity()
     * - "level" -> getLevel()
     * - "server" -> getServer()
     * - etc.
     */
    private ProxyObject createEventProxy(Object event, Context context) {
        EventProxyMetadata metadata = EVENT_PROXY_METADATA_CACHE.computeIfAbsent(event.getClass(), GraalEventHandlerProxy::buildEventProxyMetadata);
        return new EventProxy(event, context, metadata);
    }

    private static EventProxyMetadata buildEventProxyMetadata(Class<?> clazz) {
        Method[] methods = clazz.getMethods();
        Field[] fields = clazz.getFields();

        Map<String, Accessor> accessorByKey = new HashMap<>();
        java.util.Set<String> getterKeys = new java.util.HashSet<>();
        java.util.Set<String> publicFieldKeys = new java.util.HashSet<>();

        for (Field field : fields) {
            publicFieldKeys.add(field.getName());
            RemapForJS remap = field.getAnnotation(RemapForJS.class);
            if (remap != null) {
                accessorByKey.putIfAbsent(remap.value(), Accessor.forField(field, AccessorType.REMAP_FIELD));
            }
        }

        boolean hasGetEntity = false;
        for (Method method : methods) {
            if (method.getParameterCount() == 0 && "getEntity".equals(method.getName())) {
                hasGetEntity = true;
            }
            RemapForJS remap = method.getAnnotation(RemapForJS.class);
            if (remap != null) {
                accessorByKey.putIfAbsent(remap.value(), Accessor.forMethod(method, AccessorType.REMAP_METHOD));
            }
        }

        for (Method method : methods) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            String name = method.getName();
            if (name.startsWith("get") && name.length() > 3) {
                String key = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                getterKeys.add(key);
                accessorByKey.putIfAbsent(key, Accessor.forMethod(method, AccessorType.GETTER));
            }
        }

        for (Method method : methods) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            String name = method.getName();
            if (name.startsWith("is") && name.length() > 2) {
                String key = Character.toLowerCase(name.charAt(2)) + name.substring(3);
                getterKeys.add(key);
                accessorByKey.putIfAbsent(key, Accessor.forMethod(method, AccessorType.GETTER));
            }
        }

        for (Field field : fields) {
            accessorByKey.putIfAbsent(field.getName(), Accessor.forField(field, AccessorType.PUBLIC_FIELD));
        }

        for (Method method : methods) {
            if (method.getParameterCount() == 0) {
                accessorByKey.putIfAbsent(method.getName(), Accessor.forMethod(method, AccessorType.NOARG_METHOD));
            }
        }

        java.util.Set<String> keys = new java.util.HashSet<>();
        keys.addAll(publicFieldKeys);
        keys.addAll(getterKeys);

        Object[] memberKeys = keys.toArray();
        return new EventProxyMetadata(accessorByKey, memberKeys, hasGetEntity);
    }

    private static final class EventProxyMetadata {
        private final Map<String, Accessor> accessorByKey;
        private final Object[] memberKeys;
        private final boolean hasGetEntity;

        private EventProxyMetadata(
                Map<String, Accessor> accessorByKey,
                Object[] memberKeys,
                boolean hasGetEntity
        ) {
            this.accessorByKey = accessorByKey;
            this.memberKeys = memberKeys;
            this.hasGetEntity = hasGetEntity;
        }
    }

    private enum AccessorType {
        REMAP_METHOD,
        REMAP_FIELD,
        GETTER,
        PUBLIC_FIELD,
        NOARG_METHOD
    }

    private static final class Accessor {
        private final Method method;
        private final Field field;
        private final AccessorType type;

        private Accessor(Method method, Field field, AccessorType type) {
            this.method = method;
            this.field = field;
            this.type = type;
        }

        private static Accessor forMethod(Method method, AccessorType type) {
            return new Accessor(method, null, type);
        }

        private static Accessor forField(Field field, AccessorType type) {
            return new Accessor(null, field, type);
        }
    }

    private final class EventProxy implements ProxyObject {
        private final Object event;
        private final Context context;
        private final EventProxyMetadata metadata;
        private final Map<String, Object> memberCache = new HashMap<>();

        private EventProxy(Object event, Context context, EventProxyMetadata metadata) {
            this.event = event;
            this.context = context;
            this.metadata = metadata;
        }

        @Override
        public Object getMember(String key) {
            Object cached = memberCache.get(key);
            if (cached != null) {
                return cached == CACHE_MISS ? null : cached;
            }

            Accessor accessor = metadata.accessorByKey.get(key);
            if (accessor != null) {
                if (accessor.method != null) {
                    Object result = createMethodProxy(accessor.method, event, context);
                    if (result instanceof ProxyExecutable) {
                        memberCache.put(key, result);
                    }
                    return result;
                }
                if (accessor.field != null) {
                    try {
                        return wrapResult(accessor.field.get(event), context);
                    } catch (Exception e) {
                        System.err.println("[KubeLoader] Error accessing member '" + key + "': " + e.getMessage());
                        return null;
                    }
                }
            }

            memberCache.put(key, CACHE_MISS);
            return null;
        }

        @Override
        public Object getMemberKeys() {
            return metadata.memberKeys.clone();
        }

        @Override
        public boolean hasMember(String key) {
            if ("player".equals(key)) {
                return metadata.hasGetEntity;
            }

            return metadata.accessorByKey.containsKey(key);
        }

        @Override
        public void putMember(String key, Value value) {
            throw new UnsupportedOperationException("Cannot modify EventJS properties");
        }
    }
    
    /**
     * Find a no-argument method by name, searching up the class hierarchy
     */
    private java.lang.reflect.Method findMethod(Class<?> clazz, String methodName) {
        try {
            return clazz.getMethod(methodName);
        } catch (NoSuchMethodException e) {
            try {
                for (java.lang.reflect.Method m : clazz.getMethods()) {
                    if (m.getName().equals(methodName) && m.getParameterCount() == 0) {
                        m.setAccessible(true);
                        return m;
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
    
    /**
     * Create a ProxyExecutable for a Java method, or directly invoke if it's a getter.
     * 
     * Rhino behavior:
     * - Getter (no-arg method): auto-invoke and return result
     * - Method with params: return callable function
     */
    private Object createMethodProxy(java.lang.reflect.Method method, Object obj, Context context) {
        int paramCount = method.getParameterCount();
        
        if (paramCount == 0) {
            // No-arg method: treat as property getter, invoke immediately
            try {
                Object result = method.invoke(obj);
                return wrapResult(result, context);
            } catch (Exception e) {
                return null;
            }
        } else {
            // Method with parameters: return ProxyExecutable
            Class<?>[] paramTypes = method.getParameterTypes();
            return (org.graalvm.polyglot.proxy.ProxyExecutable) args -> {
                try {
                    Object[] javaArgs = new Object[paramCount];
                    
                    for (int i = 0; i < paramCount && i < args.length; i++) {
                        Object convertedArg = convertGraalValueToJava(args[i]);
                        Class<?> expectedType = paramTypes[i];
                        
                        // Try to wrap using KubeJS TypeWrappers if types don't match
                        javaArgs[i] = convertedArg;
                    }
                    Object result = method.invoke(obj, javaArgs);
                    return wrapResult(result, context);
                    
                } catch (java.lang.reflect.InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    // ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¤ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¾ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¦ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¹ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¦ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¨ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â°ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¨ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¦ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¾ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¼ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸
                    System.out.println("[KubeLoader] Method " + method.getName() + " threw exception: " + cause.getClass().getName());
                    
                    // ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â°ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¹ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¦ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â®ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¤ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¾ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â EventExitÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¼ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¯ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¼ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¹ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¨ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¿ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¾ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¦ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¹ÃƒÆ’Ã¢â‚¬Â¦ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¯ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¦ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â£ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¨ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¤ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¯ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¼ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â°
                    if (cause instanceof dev.latvian.mods.kubejs.event.EventExit) {
                        return null; // ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¨ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¿ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¾nullÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¨ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¨ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¤ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¦ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¹ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸
                    }
                    
                    // ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¦ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¶ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¤ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â»ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¼ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â©ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¨ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¦ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¨ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â®ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â°ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â½ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢
                    throw new RuntimeException("Error invoking method " + method.getName(), cause);
                    
                } catch (Exception e) {
                    throw new RuntimeException("Error invoking method", e);
                }
            };
        }
    }
    
    /**
     * Wrap result objects using RhinoAnnotationWrapper.
     * This automatically handles @RemapForJS and @RemapPrefixForJS annotations.
     */
    private Object wrapResult(Object result, Context context) {
        // Use RhinoAnnotationWrapper to handle Rhino annotations
        // It will return a ProxyObject if annotations exist, otherwise return raw object
        //return RhinoAnnotationWrapper.wrap(result);
        return WrapperHelper.wrapReturnValue(result);
    }
    
    /**
     * Check if a class has any @RemapForJS or @RemapPrefixForJS annotations
     */
    private boolean hasRemapForJSAnnotations(Class<?> clazz) {
        boolean hasAnnotations = false;

        // Check class-level @RemapPrefixForJS
        RemapPrefixForJS prefixRemap = clazz.getAnnotation(RemapPrefixForJS.class);
        if (prefixRemap != null) {
            hasAnnotations = true;
        }
        
        // Check interfaces for @RemapPrefixForJS
        for (Class<?> iface : clazz.getInterfaces()) {
            RemapPrefixForJS ifacePrefixRemap = iface.getAnnotation(RemapPrefixForJS.class);
            if (ifacePrefixRemap != null) {
                hasAnnotations = true;
            }
        }
        
        // Check methods for @RemapForJS
        for (java.lang.reflect.Method method : clazz.getMethods()) {
            RemapForJS remap = method.getAnnotation(RemapForJS.class);
            if (remap != null) {
                hasAnnotations = true;
            }
        }
        
        // Check fields for @RemapForJS
        for (java.lang.reflect.Field field : clazz.getFields()) {
            RemapForJS remap = field.getAnnotation(RemapForJS.class);
            if (remap != null) {
                hasAnnotations = true;
            }
        }

        return hasAnnotations;
    }
    
    /**
     * Create a smart proxy for objects using WrapperHelper.
     * This uses WrapperHelper.wrapObject() which handles @RemapForJS and @RemapPrefixForJS
     * annotations automatically, providing Rhino-like behavior in GraalJS.
     */
    private ProxyObject createSmartProxy(Object obj, Context context) {
        return (ProxyObject) WrapperHelper.wrapObject(obj);
    }
    
    /**
     * Thread-local storage for current ScriptType and Context
     * Should be set by KLScriptLoader when executing scripts
     */
    public static class ScriptTypeThreadLocal {
        private static final ThreadLocal<ScriptType> CURRENT_TYPE = new ThreadLocal<>();
        private static final ThreadLocal<Context> CURRENT_CONTEXT = new ThreadLocal<>();
        
        public static void set(ScriptType type, Context context) {
            CURRENT_TYPE.set(type);
            CURRENT_CONTEXT.set(context);
        }
        
        public static ScriptType get() {
            return CURRENT_TYPE.get();
        }
        
        public static Context getCurrentContext() {
            return CURRENT_CONTEXT.get();
        }
        
        public static void clear() {
            CURRENT_TYPE.remove();
            CURRENT_CONTEXT.remove();
        }
    }
    

}
