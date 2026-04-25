package com.whisent.kubeloader;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * KubeLoader 配置管理器
 * 使用Forge内置的TOML解析库
 */
public class ConfigManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    /** 配置文件路径 */
    private static final Path CONFIG_FILE_TOML = Kubeloader.ConfigPath.resolve("kubeloaderconfig.toml");
    private static final Path CONFIG_FILE_JSON = Kubeloader.ConfigPath.resolve("kubeloader.json");
    
    /** 默认配置 */
    private static KubeLoaderConfig config = new KubeLoaderConfig();
    
    /**
     * 配置数据类
     */
    public static class KubeLoaderConfig {
        /** JavaScript引擎选择: Rhino 或 GraalJS */
        @SerializedName("engine")
        private String engine = "Rhino";  // 默认为Rhino
        
        /** 是否使用现代JS转换 */
        @SerializedName("useModernJS")
        private boolean useModernJS = true;  // 默认为true
        
        // Getters
        public String getEngine() {
            return engine;
        }
        
        public boolean isUseModernJS() {
            return useModernJS;
        }
        
        // Setters
        public void setEngine(String engine) {
            this.engine = engine;
        }
        
        public void setUseModernJS(boolean useModernJS) {
            this.useModernJS = useModernJS;
        }
        
        @Override
        public String toString() {
            return "KubeLoaderConfig{engine='" + engine + "', useModernJS=" + useModernJS + "}";
        }
    }
    
    /**
     * 初始化配置管理器
     * 在模组启动时调用
     */
    public static void init() {
        LOGGER.info("[KubeLoader] 初始化配置管理器...");
        
        try {
            loadConfig();
            LOGGER.info("[KubeLoader] 配置加载完成: {}", config);
        } catch (Exception e) {
            LOGGER.error("[KubeLoader] 配置加载失败，使用默认配置", e);
            // 使用默认配置
            config = new KubeLoaderConfig();
        }
    }
    
    /**
     * 加载配置文件
     * 优先加载 TOML 格式，如果不存在则尝试JSON格式
     */
    private static void loadConfig() throws IOException {
        // 优先检查 TOML 文件
        if (Files.exists(CONFIG_FILE_TOML)) {
            loadTomlConfig();
        }
        // 都不存在则创建默认TOML配置
        else {
            // 检测 GraalJS 是否可用
            boolean graalJSAvailable = checkGraalJSAvailable();
            if (graalJSAvailable) {
                config.setEngine("GraalJS");
                LOGGER.info("[KubeLoader] 检测到 GraalJS 可用，将使用 GraalJS 作为默认引擎");
            } else {
                config.setEngine("Rhino");
                LOGGER.info("[KubeLoader] GraalJS 不可用，将使用 Rhino 作为默认引擎");
            }
            saveDefaultConfig();
            LOGGER.info("[KubeLoader] 创建默认TOML配置文件: {}", CONFIG_FILE_TOML);
        }
        
        // 验证配置值
        validateConfig();
    }
    
    /**
     * 检测 GraalJS 是否可用
     */
    private static boolean checkGraalJSAvailable() {
        try {
            Class.forName("graal.graalvm.polyglot.Engine");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * 加载 TOML 配置文件
     */
    private static void loadTomlConfig() throws IOException {
        try {
            CommentedFileConfig configData = CommentedFileConfig.builder(CONFIG_FILE_TOML)
                    .sync()
                    .writingMode(WritingMode.REPLACE)
                    .build();
            configData.load();
            
            // 从 TOML 读取配置
            String engine = configData.getOrElse("engine", "Rhino");
            Boolean useModernJS = configData.getOrElse("useModernJS", true);
            
            config.setEngine(engine);
            config.setUseModernJS(useModernJS);
            
            configData.close();
            LOGGER.info("[KubeLoader] 读取TOML配置文件: {}", CONFIG_FILE_TOML);
        } catch (Exception e) {
            LOGGER.error("[KubeLoader] TOML配置文件解析失败，尝试创建新的配置", e);
            saveDefaultConfig();
        }
    }
    
    /**
     * 验证配置值的有效性
     */
    private static void validateConfig() {
        // 验证engine值
        if (!"Rhino".equalsIgnoreCase(config.getEngine()) && 
            !"GraalJS".equalsIgnoreCase(config.getEngine())) {
            LOGGER.warn("[KubeLoader] 无效的engine值 '{}', 使用默认值 'Rhino'", config.getEngine());
            config.setEngine("Rhino");
        }
        
        // 确保engine首字母大写
        config.setEngine(capitalize(config.getEngine()));
    }
    
    /**
     * 首字母大写
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
    
    /**
     * 保存默认配置到文件（TOML格式）
     */
    private static void saveDefaultConfig() throws IOException {
        // 确保配置目录存在
        if (!Files.exists(Kubeloader.ConfigPath)) {
            Files.createDirectories(Kubeloader.ConfigPath);
        }
        
        saveAsToml();
    }
    
    /**
     * 保存为TOML格式
     */
    private static void saveAsToml() throws IOException {
        CommentedFileConfig configData = CommentedFileConfig.builder(CONFIG_FILE_TOML)
                .sync()
                .writingMode(WritingMode.REPLACE)
                .build();
        
        configData.set("engine", config.getEngine());
        configData.set("useModernJS", config.isUseModernJS());
        
        // 添加注释
        configData.setComment("engine", "JavaScript引擎选择: \"Rhino\" 或 \"GraalJS\"");
        configData.setComment("useModernJS", "是否启用现代JavaScript语法转换");
        
        configData.save();
        configData.close();
    }
    
    /**
     * 获取当前配置
     */
    public static KubeLoaderConfig getConfig() {
        return config;
    }
    
    /**
     * 检查是否应该使用GraalJS引擎
     */
    public static boolean shouldUseGraalJS() {
        return "GraalJS".equalsIgnoreCase(config.getEngine());
    }
    
    /**
     * 检查是否应该使用现代JS转换
     */
    public static boolean shouldUseModernJS() {
        return config.isUseModernJS();
    }
    
    /**
     * 检查是否应该进行源代码转换
     * 结合引擎选择和现代JS设置
     */
    public static boolean shouldTransformSource() {
        // 如果使用Rhino引擎且启用了现代JS转换，则需要转换
        // 如果使用GraalJS引擎，通常不需要额外转换（GraalJS本身支持现代语法）
        return !"GraalJS".equalsIgnoreCase(config.getEngine()) && config.isUseModernJS();
    }
}