package com.whisent.kubeloader.graal;

import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ConsoleJS;
import org.graalvm.polyglot.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;

public class GraalApi {

    /**
     * 向指定 Context 注册绑定
     * - 如果 value 是 Class<?>，则在 JS 中绑定为可调用静态方法的类（等效于 Java.type('...')）
     * - 否则，直接绑定该 Java 对象（需 allowHostAccess 已启用）
     */
    public static void registerBinding(Context context, String name, Object value) {
        if (context == null || name == null || value == null) {
            throw new IllegalArgumentException("Context, name and value must not be null");
        }
        if (value instanceof Class<?>) {
            Class<?> clazz = (Class<?>) value;
            String className = clazz.getName();
            // 获取 JS 上下文中的 Java.type 函数
            Value javaObj = context.getBindings("js").getMember("Java");
            if (javaObj == null || javaObj.isNull()) {
                throw new IllegalStateException("Java object not available in JS context. " +
                        "Make sure you have initialized Java interop (e.g., via Java.type).");
            }

            Value typeFunc = javaObj.getMember("type");
            if (typeFunc == null || !typeFunc.canExecute()) {
                throw new IllegalStateException("Java.type function is not available in JS context.");
            }

            // 执行 Java.type(className)
            Value classRef = typeFunc.execute(className);
            // 将类引用绑定到 JS
            context.getBindings("js").putMember(name, classRef);
        } else {
            // 普通 Java 对象，直接绑定（依赖 allowHostAccess）
            context.getBindings("js").putMember(name, value);
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

    public static void eval(Context context, String script, String filePath, ScriptType type) {
        if (context == null || script == null) return;

        try {
            Source source = Source.newBuilder("js", script, filePath).build();
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

        Context ctx = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(s -> true)
                .option("engine.WarnInterpreterOnly", "false")
                .build();

        // 直接在 JS 中定义 loadClass = Java.type
        ctx.eval("js", """
        if (typeof Java !== 'undefined' && !Java.loadClass) {
            Java.loadClass = Java.type;
        }
    """);
        return ctx;
    }
}