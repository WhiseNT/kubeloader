package com.whisent.kubeloader.compat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * GraalJS 兼容性检测工具类
 * 检查 GraalJS 相关依赖是否可用，确保在没有安装 GraalJS 时不出现 ClassNotFoundException
 */
public class GraalJSCompat {
    private static final Logger LOGGER = LogManager.getLogger();
    
    /** GraalJS 核心依赖是否可用 */
    public static boolean isGraalJSAvailable = false;

    
    /** 是否可以安全使用 GraalJS 功能 */
    public static boolean canUseGraalJS = false;
    
    /**
     * 初始化兼容性检测
     * 在模组加载早期调用此方法
     */
    public static void init() {
        // 检查类路径中的核心类是否存在
        isGraalJSAvailable = checkGraalJSCoreClasses();
        // 确定是否可以使用 GraalJS 功能
        canUseGraalJS = isGraalJSAvailable;
    }
    
    /**
     * 检查 GraalJS 核心类是否存在
     */
    private static boolean checkGraalJSCoreClasses() {
        try {
            // 尝试加载 GraalJS 的核心类
            Class.forName("org.graalvm.polyglot.Context");
            Class.forName("org.graalvm.polyglot.Value");
            Class.forName("org.graalvm.polyglot.Source");
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            LOGGER.debug("[KubeLoader] GraalJS 核心类加载失败: {}", e.getMessage());
            return false;
        } catch (Throwable t) {
            LOGGER.warn("[KubeLoader] 检查 GraalJS 核心类时发生未知错误", t);
            return false;
        }
    }
    
    /**
     * 安全地创建 GraalJS Context
     * 如果依赖不可用则返回 null
     */
    public static Object createSafeContext() {
        if (!canUseGraalJS) {
            LOGGER.warn("[KubeLoader] 尝试创建 GraalJS Context 但依赖不可用");
            return null;
        }
        
        try {
            // 延迟加载 GraalApi 类以避免早期初始化问题
            Class<?> graalApiClass = Class.forName("com.whisent.kubeloader.graal.GraalApi");
            java.lang.reflect.Method createContextMethod = graalApiClass.getMethod("createContext");
            return createContextMethod.invoke(null);
        } catch (Exception e) {
            LOGGER.error("[KubeLoader] 创建 GraalJS Context 失败", e);
            return null;
        }
    }
    
    /**
     * 检查是否可以安全使用 GraalJS 功能
     */
    public static boolean isGraalJSSafeToUse() {
        return canUseGraalJS;
    }
}