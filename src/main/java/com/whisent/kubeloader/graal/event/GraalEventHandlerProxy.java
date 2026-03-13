 package com.whisent.kubeloader.graal.event;

import com.whisent.kubeloader.graal.wrapper.RhinoAnnotationWrapper;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.IEventHandler;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ListJS;
import dev.latvian.mods.kubejs.util.UtilsJS;
import dev.latvian.mods.rhino.util.RemapForJS;
import dev.latvian.mods.rhino.util.RemapPrefixForJS;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

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
        
        // Fallback: try to detect from stack or default to STARTUP
        // This is not ideal but prevents crashes
        System.err.println("[KubeLoader] Warning: Could not determine ScriptType, defaulting to STARTUP");
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
        return new ProxyObject() {
            @Override
            public Object getMember(String key) {
                try {
                    // First, check all methods for @RemapForJS annotation
                    for (java.lang.reflect.Method method : event.getClass().getMethods()) {
                        RemapForJS remap = method.getAnnotation(RemapForJS.class);
                        if (remap != null && remap.value().equals(key)) {
                            // Found a method with matching @RemapForJS annotation
                            // ALWAYS return ProxyExecutable, even for no-arg methods
                            return createMethodProxy(method, event, context);
                        }
                    }
                    
                    // Also check fields for @RemapForJS annotation
                    for (java.lang.reflect.Field field : event.getClass().getFields()) {
                        RemapForJS remap = field.getAnnotation(RemapForJS.class);
                        if (remap != null && remap.value().equals(key)) {
                            Object result = field.get(event);
                            return wrapResult(result, context);
                        }
                    }
                    
                } catch (Exception e) {
                    System.err.println("[KubeLoader] Error checking @RemapForJS annotations for '" + key + "': " + e.getMessage());
                }
                
                // Special mappings for common fields that don't follow getter naming conventions
                
                // Try to find a getter method for this key
                String getterName = "get" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
                String isGetterName = "is" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
                
                try {
                    // Try "getXxx()" pattern - return as ProxyExecutable
                    java.lang.reflect.Method getter = findMethod(event.getClass(), getterName);
                    if (getter != null) {
                        return createMethodProxy(getter, event, context);
                    }
                    
                    // Try "isXxx()" pattern for booleans
                    getter = findMethod(event.getClass(), isGetterName);
                    if (getter != null) {
                        return createMethodProxy(getter, event, context);
                    }
                    
                    // Try direct field access (for public fields)
                    try {
                        java.lang.reflect.Field field = event.getClass().getField(key);
                        Object result = field.get(event);
                        return wrapResult(result, context);
                    } catch (NoSuchFieldException ignored) {
                        // Field doesn't exist
                    }
                    
                    // Try method with same name as key
                    java.lang.reflect.Method method = findMethod(event.getClass(), key);
                    if (method != null) {
                        return createMethodProxy(method, event, context);
                    }
                    
                } catch (Exception e) {
                    System.err.println("[KubeLoader] Error accessing member '" + key + "': " + e.getMessage());
                }
                
                // If all fails, return null
                return null;
            }
            
            @Override
            public Object getMemberKeys() {
                // Return all possible member names (fields + getter methods)
                java.util.Set<String> keys = new java.util.HashSet<>();
                
                // Add all public fields
                for (java.lang.reflect.Field field : event.getClass().getFields()) {
                    keys.add(field.getName());
                }
                
                // Add all getter methods (getXxx, isXxx)
                for (java.lang.reflect.Method method : event.getClass().getMethods()) {
                    String name = method.getName();
                    if (method.getParameterCount() == 0) {
                        if (name.startsWith("get") && name.length() > 3) {
                            // "getFoo" -> "foo"
                            String key = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                            keys.add(key);
                        } else if (name.startsWith("is") && name.length() > 2) {
                            // "isFoo" -> "foo"
                            String key = Character.toLowerCase(name.charAt(2)) + name.substring(3);
                            keys.add(key);
                        }
                    }
                }
                
                return keys.toArray();
            }
            
            @Override
            public boolean hasMember(String key) {
                // Check for @RemapForJS annotation first
                for (java.lang.reflect.Method method : event.getClass().getMethods()) {
                    RemapForJS remap = method.getAnnotation(RemapForJS.class);
                    if (remap != null && remap.value().equals(key)) {
                        return true;
                    }
                }
                
                for (java.lang.reflect.Field field : event.getClass().getFields()) {
                    RemapForJS remap = field.getAnnotation(RemapForJS.class);
                    if (remap != null && remap.value().equals(key)) {
                        return true;
                    }
                }
                
                // Special mapping: "player" -> getEntity()
                if ("player".equals(key)) {
                    return findMethod(event.getClass(), "getEntity") != null;
                }
                
                // Check if we can access this member
                String getterName = "get" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
                String isGetterName = "is" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
                
                // Check for getter methods
                if (findMethod(event.getClass(), getterName) != null) {
                    return true;
                }
                if (findMethod(event.getClass(), isGetterName) != null) {
                    return true;
                }
                
                // Check for public field
                try {
                    event.getClass().getField(key);
                    return true;
                } catch (NoSuchFieldException ignored) {
                }
                
                // Check for method with same name
                if (findMethod(event.getClass(), key) != null) {
                    return true;
                }
                
                return false;
            }
            
            @Override
            public void putMember(String key, org.graalvm.polyglot.Value value) {
                // EventJS objects are typically immutable, but we can support this if needed
                throw new UnsupportedOperationException("Cannot modify EventJS properties");
            }
        };
    }
    
    /**
     * Find a no-argument method by name, searching up the class hierarchy
     */
    private java.lang.reflect.Method findMethod(Class<?> clazz, String methodName) {
        try {
            java.lang.reflect.Method method = clazz.getMethod(methodName);
            if (method.getParameterCount() == 0) {
                return method;
            }
        } catch (NoSuchMethodException ignored) {
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
                System.err.println("[KubeLoader] Error invoking getter '" + method.getName() + "': " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        } else {
            // Method with parameters: return ProxyExecutable
            return (org.graalvm.polyglot.proxy.ProxyExecutable) args -> {
                try {
                    System.out.println("[KubeLoader] Invoking method: " + method.getName() + " with " + args.length + " args on " + obj.getClass().getName());
                    
                    Class<?>[] paramTypes = method.getParameterTypes();
                    Object[] javaArgs = new Object[paramCount];
                    
                    for (int i = 0; i < paramCount && i < args.length; i++) {
                        Object convertedArg = convertGraalValueToJava(args[i]);
                        Class<?> expectedType = paramTypes[i];
                        
                        // Try to wrap using KubeJS TypeWrappers if types don't match
                        if (convertedArg != null && !expectedType.isInstance(convertedArg)) {
                            System.out.println("[KubeLoader]   Type mismatch: expected " + expectedType.getName() + ", got " + convertedArg.getClass().getName());

                        }
                        
                        System.out.println("[KubeLoader]   Arg[" + i + "]: " + convertedArg + " (" + (convertedArg != null ? convertedArg.getClass().getName() : "null") + ")");
                        javaArgs[i] = convertedArg;
                    }
                    
                    System.out.println("[KubeLoader] Calling method.invoke...");
                    Object result = method.invoke(obj, javaArgs);
                    System.out.println("[KubeLoader] Method invoked successfully, result: " + result);
                    return wrapResult(result, context);
                    
                } catch (java.lang.reflect.InvocationTargetException e) {
                    // ÃƒÂ¥Ã‚Â¤Ã¢â‚¬Å¾ÃƒÂ§Ã‚ÂÃ¢â‚¬Â ÃƒÂ¦Ã¢â‚¬â€œÃ‚Â¹ÃƒÂ¦Ã‚Â³Ã¢â‚¬Â¢ÃƒÂ¨Ã‚Â°Ã†â€™ÃƒÂ§Ã¢â‚¬ÂÃ‚Â¨ÃƒÂ¦Ã…Â Ã¢â‚¬ÂºÃƒÂ¥Ã¢â‚¬Â¡Ã‚ÂºÃƒÂ§Ã…Â¡Ã¢â‚¬Å¾ÃƒÂ¥Ã‚Â¼Ã¢â‚¬Å¡ÃƒÂ¥Ã‚Â¸Ã‚Â¸
                    Throwable cause = e.getCause();
                    System.out.println("[KubeLoader] Method " + method.getName() + " threw exception: " + cause.getClass().getName());
                    
                    // ÃƒÂ§Ã¢â‚¬Â°Ã‚Â¹ÃƒÂ¦Ã‚Â®Ã…Â ÃƒÂ¥Ã‚Â¤Ã¢â‚¬Å¾ÃƒÂ§Ã‚ÂÃ¢â‚¬Â EventExitÃƒÂ¥Ã‚Â¼Ã¢â‚¬Å¡ÃƒÂ¥Ã‚Â¸Ã‚Â¸ÃƒÂ¯Ã‚Â¼Ã‹â€ ÃƒÂ¨Ã‚Â¿Ã¢â€žÂ¢ÃƒÂ¦Ã‹Å“Ã‚Â¯ÃƒÂ¦Ã‚Â­Ã‚Â£ÃƒÂ¥Ã‚Â¸Ã‚Â¸ÃƒÂ¨Ã‚Â¡Ã…â€™ÃƒÂ¤Ã‚Â¸Ã‚ÂºÃƒÂ¯Ã‚Â¼Ã¢â‚¬Â°
                    if (cause instanceof dev.latvian.mods.kubejs.event.EventExit) {
                        System.out.println("[KubeLoader] EventExit thrown (normal for cancel operations)");
                        return null; // ÃƒÂ¨Ã‚Â¿Ã¢â‚¬ÂÃƒÂ¥Ã¢â‚¬ÂºÃ…Â¾nullÃƒÂ¨Ã‚Â¡Ã‚Â¨ÃƒÂ§Ã‚Â¤Ã‚ÂºÃƒÂ¦Ã‹â€ Ã‚ÂÃƒÂ¥Ã…Â Ã…Â¸
                    }
                    
                    // ÃƒÂ¥Ã¢â‚¬Â¦Ã‚Â¶ÃƒÂ¤Ã‚Â»Ã¢â‚¬â€œÃƒÂ¥Ã‚Â¼Ã¢â‚¬Å¡ÃƒÂ¥Ã‚Â¸Ã‚Â¸ÃƒÂ©Ã…â€œÃ¢â€šÂ¬ÃƒÂ¨Ã‚Â¦Ã‚ÂÃƒÂ¨Ã‚Â®Ã‚Â°ÃƒÂ¥Ã‚Â½Ã¢â‚¬Â¢
                    System.err.println("[KubeLoader] Method invocation failed: " + cause.getMessage());
                    cause.printStackTrace();
                    throw new RuntimeException("Error invoking method " + method.getName(), cause);
                    
                } catch (Exception e) {
                    System.err.println("[KubeLoader] Error invoking method '" + method.getName() + "': " + e.getClass().getName() + ": " + e.getMessage());
                    if (e.getCause() != null) {
                        System.err.println("[KubeLoader]   Caused by: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
                        e.getCause().printStackTrace();
                    }
                    e.printStackTrace();
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
        return RhinoAnnotationWrapper.wrap(result);
    }
    
    /**
     * Check if a class has any @RemapForJS or @RemapPrefixForJS annotations
     */
    private boolean hasRemapForJSAnnotations(Class<?> clazz) {
        boolean hasAnnotations = false;
        
        // Debug: print all @RemapForJS and @RemapPrefixForJS annotations found
        System.out.println("[KubeLoader] Checking @RemapForJS/@RemapPrefixForJS annotations on " + clazz.getName());
        
        // Check class-level @RemapPrefixForJS
        RemapPrefixForJS prefixRemap = clazz.getAnnotation(RemapPrefixForJS.class);
        if (prefixRemap != null) {
            System.out.println("[KubeLoader]   Found @RemapPrefixForJS(\"" + prefixRemap.value() + "\") on class: " + clazz.getName());
            hasAnnotations = true;
        }
        
        // Check interfaces for @RemapPrefixForJS
        for (Class<?> iface : clazz.getInterfaces()) {
            RemapPrefixForJS ifacePrefixRemap = iface.getAnnotation(RemapPrefixForJS.class);
            if (ifacePrefixRemap != null) {
                System.out.println("[KubeLoader]   Found @RemapPrefixForJS(\"" + ifacePrefixRemap.value() + "\") on interface: " + iface.getName());
                hasAnnotations = true;
            }
        }
        
        // Check methods for @RemapForJS
        for (java.lang.reflect.Method method : clazz.getMethods()) {
            RemapForJS remap = method.getAnnotation(RemapForJS.class);
            if (remap != null) {
                System.out.println("[KubeLoader]   Found @RemapForJS(\"" + remap.value() + "\") on method: " + method.getName());
                hasAnnotations = true;
            }
        }
        
        // Check fields for @RemapForJS
        for (java.lang.reflect.Field field : clazz.getFields()) {
            RemapForJS remap = field.getAnnotation(RemapForJS.class);
            if (remap != null) {
                System.out.println("[KubeLoader]   Found @RemapForJS(\"" + remap.value() + "\") on field: " + field.getName());
                hasAnnotations = true;
            }
        }
        
        if (!hasAnnotations) {
            System.out.println("[KubeLoader]   No @RemapForJS/@RemapPrefixForJS annotations found");
        }
        
        return hasAnnotations;
    }
    
    /**
     * Create a smart proxy for objects with @RemapForJS annotations
     * This mimics Rhino's behavior of mapping JS property names to Java methods/fields
     */
    private ProxyObject createSmartProxy(Object obj, Context context) {
        return new ProxyObject() {
            @Override
            public Object getMember(String key) {
                try {
                    // PRIORITY 0: Check for @RemapPrefixForJS on class level
                    RemapPrefixForJS prefixRemap = obj.getClass().getAnnotation(RemapPrefixForJS.class);
                    if (prefixRemap != null) {
                        String prefix = prefixRemap.value();
                        String prefixedMethodName = prefix + key;
                        
                        // Try to find method with prefix (e.g., "kjs$tell" for key="tell")
                        for (java.lang.reflect.Method method : obj.getClass().getMethods()) {
                            if (method.getName().equals(prefixedMethodName)) {
                                System.out.println("[KubeLoader] @RemapPrefixForJS matched: " + prefixedMethodName + " -> " + key);
                                return createMethodProxy(method, obj, context);
                            }
                        }
                    }
                    
                    // Also check all interfaces for @RemapPrefixForJS
                    for (Class<?> iface : obj.getClass().getInterfaces()) {
                        RemapPrefixForJS ifacePrefixRemap = iface.getAnnotation(RemapPrefixForJS.class);
                        if (ifacePrefixRemap != null) {
                            String prefix = ifacePrefixRemap.value();
                            String prefixedMethodName = prefix + key;
                            
                            // Try to find method with prefix
                            for (java.lang.reflect.Method method : obj.getClass().getMethods()) {
                                if (method.getName().equals(prefixedMethodName)) {
                                    System.out.println("[KubeLoader] @RemapPrefixForJS (interface) matched: " + prefixedMethodName + " -> " + key);
                                    return createMethodProxy(method, obj, context);
                                }
                            }
                        }
                    }
                    
                    // PRIORITY 1: Check for @RemapForJS on methods (any parameter count)
                    for (java.lang.reflect.Method method : obj.getClass().getMethods()) {
                        RemapForJS remap = method.getAnnotation(RemapForJS.class);
                        if (remap != null && remap.value().equals(key)) {
                            System.out.println("[KubeLoader] @RemapForJS matched: " + method.getName() + " -> " + key);
                            return createMethodProxy(method, obj, context);
                        }
                    }
                    
                    // PRIORITY 2: Check for @RemapForJS on fields
                    for (java.lang.reflect.Field field : obj.getClass().getFields()) {
                        RemapForJS remap = field.getAnnotation(RemapForJS.class);
                        if (remap != null && remap.value().equals(key)) {
                            System.out.println("[KubeLoader] @RemapForJS field matched: " + field.getName() + " -> " + key);
                            Object result = field.get(obj);
                            return wrapResult(result, context);
                        }
                    }
                    
                    // PRIORITY 3: Try standard JavaBean getter pattern
                    String getterName = "get" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
                    java.lang.reflect.Method getter = findMethod(obj.getClass(), getterName);
                    if (getter != null) {
                        return createMethodProxy(getter, obj, context);
                    }
                    
                    // PRIORITY 4: Try "isXxx" pattern for booleans
                    String isGetterName = "is" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
                    java.lang.reflect.Method isGetter = findMethod(obj.getClass(), isGetterName);
                    if (isGetter != null) {
                        return createMethodProxy(isGetter, obj, context);
                    }
                    
                    // PRIORITY 5: Try direct method with same name (no parameters)
                    java.lang.reflect.Method method = findMethod(obj.getClass(), key);
                    if (method != null) {
                        return createMethodProxy(method, obj, context);
                    }
                    
                    // PRIORITY 6: Try direct field access
                    try {
                        java.lang.reflect.Field field = obj.getClass().getField(key);
                        Object result = field.get(obj);
                        return wrapResult(result, context);
                    } catch (NoSuchFieldException ignored) {
                    }
                    
                } catch (Exception e) {
                    System.err.println("[KubeLoader] Error in smart proxy getMember('" + key + "'): " + e.getMessage());
                    e.printStackTrace();
                }
                
                return null;
            }
            
            @Override
            public Object getMemberKeys() {
                java.util.Set<String> keys = new java.util.HashSet<>();
                
                // Add all @RemapForJS names
                for (java.lang.reflect.Method method : obj.getClass().getMethods()) {
                    RemapForJS remap = method.getAnnotation(RemapForJS.class);
                    if (remap != null) {
                        keys.add(remap.value());
                    }
                }
                
                for (java.lang.reflect.Field field : obj.getClass().getFields()) {
                    RemapForJS remap = field.getAnnotation(RemapForJS.class);
                    if (remap != null) {
                        keys.add(remap.value());
                    }
                }
                
                // Add getter methods
                for (java.lang.reflect.Method method : obj.getClass().getMethods()) {
                    String name = method.getName();
                    if (method.getParameterCount() == 0) {
                        if (name.startsWith("get") && name.length() > 3) {
                            String key = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                            keys.add(key);
                        } else if (name.startsWith("is") && name.length() > 2) {
                            String key = Character.toLowerCase(name.charAt(2)) + name.substring(3);
                            keys.add(key);
                        }
                    }
                }
                
                // Add public fields
                for (java.lang.reflect.Field field : obj.getClass().getFields()) {
                    keys.add(field.getName());
                }
                
                // Add direct methods (including cancel)
                for (java.lang.reflect.Method method : obj.getClass().getMethods()) {
                    if (!method.getName().startsWith("get") && !method.getName().startsWith("is")) {
                        keys.add(method.getName());
                    }
                }
                
                return keys.toArray();
            }
            
            @Override
            public boolean hasMember(String key) {
                // Check @RemapPrefixForJS on class level
                RemapPrefixForJS prefixRemap = obj.getClass().getAnnotation(RemapPrefixForJS.class);
                if (prefixRemap != null) {
                    String prefix = prefixRemap.value();
                    String prefixedMethodName = prefix + key;
                    
                    // Check if method with prefix exists
                    for (java.lang.reflect.Method method : obj.getClass().getMethods()) {
                        if (method.getName().equals(prefixedMethodName)) {
                            return true;
                        }
                    }
                }
                
                // Check interfaces for @RemapPrefixForJS
                for (Class<?> iface : obj.getClass().getInterfaces()) {
                    RemapPrefixForJS ifacePrefixRemap = iface.getAnnotation(RemapPrefixForJS.class);
                    if (ifacePrefixRemap != null) {
                        String prefix = ifacePrefixRemap.value();
                        String prefixedMethodName = prefix + key;
                        
                        // Check if method with prefix exists
                        for (java.lang.reflect.Method method : obj.getClass().getMethods()) {
                            if (method.getName().equals(prefixedMethodName)) {
                                return true;
                            }
                        }
                    }
                }
                
                // Check @RemapForJS on methods
                for (java.lang.reflect.Method method : obj.getClass().getMethods()) {
                    RemapForJS remap = method.getAnnotation(RemapForJS.class);
                    if (remap != null && remap.value().equals(key)) {
                        return true;
                    }
                }
                
                // Check @RemapForJS on fields
                for (java.lang.reflect.Field field : obj.getClass().getFields()) {
                    RemapForJS remap = field.getAnnotation(RemapForJS.class);
                    if (remap != null && remap.value().equals(key)) {
                        return true;
                    }
                }
                
                // Check standard getter
                String getterName = "get" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
                if (findMethod(obj.getClass(), getterName) != null) {
                    return true;
                }
                
                // Check "isXxx" getter
                String isGetterName = "is" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
                if (findMethod(obj.getClass(), isGetterName) != null) {
                    return true;
                }
                
                // Check direct method
                if (findMethod(obj.getClass(), key) != null) {
                    return true;
                }
                
                // Check direct field
                try {
                    obj.getClass().getField(key);
                    return true;
                } catch (NoSuchFieldException ignored) {
                }
                
                return false;
            }
            
            @Override
            public void putMember(String key, org.graalvm.polyglot.Value value) {
                throw new UnsupportedOperationException("Cannot modify properties");
            }
        };
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