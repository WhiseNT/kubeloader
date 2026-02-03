package com.whisent.kubeloader.graal;

import com.whisent.kubeloader.graal.wrapper.CustomHostAccess;
import com.whisent.kubeloader.graal.wrapper.WrapperHelper;
import com.whisent.kubeloader.impl.mixin.GraalPack;
import com.whisent.kubeloader.impl.mixin.ScriptManagerInterface;
import dev.latvian.mods.kubejs.event.EventGroupWrapper;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ConsoleJS;
import org.graalvm.polyglot.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;

public class GraalApi {
    
    private static boolean wrappersInitialized = false;

    /**
     * 向指定 Context 注册绑定
     * - 如果 value 是 Class<?>，则在 JS 中绑定为可调用静态方法的类（等效于 Java.type('...')）
     * - 否则，直接绑定该 Java 对象（需 allowHostAccess 已启用）
     */
    public static void registerBinding(Context context, String name, Object value) {
        if (context == null || name == null || value == null) {
            throw new IllegalArgumentException("Context, name and value must not be null");
        }
        System.out.println("[KubeLoader] Binding " + name + " to " + value.getClass());
        // 特殊处理EventGroupWrapper类型（来自KubeJS）
        if (value instanceof EventGroupWrapper eventGroupWrapper) {
            // 使用动态代理包装EventGroupWrapper
            try {

                return;
            } catch (Exception e) {
                System.err.println("[KubeLoader] Failed to create EventGroupWrapperProxy for " + name + ": " + e.getMessage());
                e.printStackTrace();
                // 如果代理类创建失败，回退到直接绑定
                context.getBindings("js").putMember(name, value);
            }
            return;
        } else {
            if (value instanceof Class<?>) {
                Class<?> clazz = (Class<?>) value;
                String className = clazz.getName();

                // 首先尝试通过Java.type方式绑定（保持原有功能）
                Value javaObj = context.getBindings("js").getMember("Java");
                if (javaObj != null && !javaObj.isNull()) {
                    Value typeFunc = javaObj.getMember("type");
                    if (typeFunc != null && typeFunc.canExecute()) {
                        // 执行 Java.type(className)
                        Value classRef = typeFunc.execute(className);
                        // 将类引用绑定到 JS
                        context.getBindings("js").putMember(name, classRef);
                        return;
                    }
                }

                // 如果Java.type不可用，则回退到使用context.asValue(clazz)
                Value classRef = context.asValue(clazz);
                context.getBindings("js").putMember(name, classRef);
            } else {
                // 普通 Java 对象，直接绑定（依赖 allowHostAccess）
                context.getBindings("js").putMember(name, value);
            }
        }


    }

    /**
     * 在指定 Context 中执行 JS 脚本
     */
    public static void eval(Context context, String script) {
        if (context == null || script == null) return;
        try {
            context.eval("js", script);
        } catch (PolyglotException e) {
            System.err.println("[GraalJS] Script error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void eval(Context context, String script, ScriptFileInfo info, ScriptPack pack) {
        String filePath = info.location;
        ScriptType type = pack.manager.scriptType;
        if (context == null || script == null) return;

        try {
            Source source = Source.newBuilder("js", script, filePath).build();
            GraalPack graalPack = (GraalPack) pack;
            graalPack.kubeLoader$getDynamicGraalConsole().setSourceLocation(filePath);
            context.eval(source);
        } catch (PolyglotException e) {

            String linePart = e.getSourceLocation().getStartLine() + "";
            if (type == null) {
                System.err.println("[GraalJS] " + filePath + "#" + linePart + ": " + e.getMessage());
                e.printStackTrace();
                return;
            }
            if (type == ScriptType.STARTUP) {
                ConsoleJS.STARTUP.error(filePath+ "#" + linePart + ": " + e.getMessage());
                ConsoleJS.STARTUP.error(e);
            } else if (type == ScriptType.SERVER) {
                ConsoleJS.SERVER.error(filePath+ "#" + linePart + ": " + e.getMessage());
                ConsoleJS.SERVER.error(e);
            } else if (type == ScriptType.CLIENT) {
                ConsoleJS.CLIENT.error(filePath+ "#" + linePart + ": " + e.getMessage());
                ConsoleJS.CLIENT.error(e);
            } else {
                System.err.println("[GraalJS] " + filePath + "#" + linePart + ": " + e.getMessage());
                e.printStackTrace();
            }
            // 输出格式: filePath#line: 错误信息


        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // 只提取行号（忽略列号），返回 "#6" 或 ""
    private static String extractLineNumberFromMessage(String message) {
        if (message == null) return "";

        // 查找类似 ":6:153" 或 ":6:153-154" 的模式，并确保它在括号内
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\\((?:[^)]*?)(\\d+):\\d+(?:-\\d+)?\\)"
        );
        java.util.regex.Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            return "#" + matcher.group(1);
        }
        return "";
    }
    public static void readBuiltinScripts(Context context) {
        try {
            // 获取资源目录下的所有脚本文件
            ClassLoader classLoader = GraalApi.class.getClassLoader();
            URL resourceUrl = classLoader.getResource("scripts/");

            if (resourceUrl == null) {
                System.err.println("[GraalJS] Builtin scripts directory not found");
                return;
            }

            if ("file".equals(resourceUrl.getProtocol())) {
                // 开发环境 - 从文件系统读取
                File dir = new File(resourceUrl.toURI());
                if (dir.exists() && dir.isDirectory()) {
                    File[] scripts = dir.listFiles((d, name) -> name.endsWith(".js"));
                    if (scripts != null) {
                        for (File scriptFile : scripts) {
                            try {
                                String scriptContent = Files.readString(scriptFile.toPath());
                                eval(context, scriptContent);
                                System.out.println("[GraalJS] Loaded script: " + scriptFile.getName());
                            } catch (Exception e) {
                                System.err.println("[GraalJS] Error loading script " + scriptFile.getName() + ": " + e.getMessage());
                            }
                        }
                    }
                }
            } else {
                // 运行环境 - 遍历所有资源文件（简化处理，实际项目中可能需要更复杂的逻辑）
                System.err.println("[GraalJS] Reading scripts from JAR is not fully implemented in this example");
            }
        } catch (Exception e) {
            System.err.println("[GraalJS] Error reading builtin scripts: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * 创建一个预配置的 GraalJS Context（推荐配置）
     */

    public static Context createContext() {
        // Initialize type wrappers on first use (fallback for standalone usage)
        // Note: TypeWrappers will be automatically synced via TypeWrappersMixin when KubeJS registers them
        if (!wrappersInitialized) {
            //BuiltinTypeWrappers.init();
            wrappersInitialized = true;
        }

        // Use custom HostAccess that applies type wrappers automatically
        Context ctx = Context.newBuilder("js")
                .allowHostAccess(CustomHostAccess.create())  // Use custom host access with wrapper support
                .allowHostClassLookup(s -> true)
                .option("engine.WarnInterpreterOnly", "false")
                .build();

        // 直接在 JS 中定义 loadClass = Java.type
        ctx.eval("js", """
        if (typeof Java !== 'undefined' && !Java.loadClass) {
            Java.loadClass = Java.type;
        }
    """);
        
        // Register WrapperHelper for advanced usage
        WrapperHelper.registerInContext(ctx);
        
        // Load wrapper compatibility layer
        loadWrapperCompatLayer(ctx);
        
        System.out.println("[KubeLoader] GraalJS Context created with automatic type wrapper support");
        System.out.println("[KubeLoader] Total registered type wrappers: " + 
                com.whisent.kubeloader.graal.wrapper.TypeWrapperRegistry.getRegisteredTypes().size());
        
        return ctx;
    }

    public static Context createContext(ScriptManager manager) {
        ScriptManagerInterface thiz = (ScriptManagerInterface) manager;
        Context ctx = createContext();
//        registerBinding(ctx,"ForgeEvents",new ForgeEventWrapper("ForgeEvents", MinecraftForge.EVENT_BUS));
        thiz.getKubeLoader$bindings().forEach((name,value)->{
            registerBinding(ctx,name,value);
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
}