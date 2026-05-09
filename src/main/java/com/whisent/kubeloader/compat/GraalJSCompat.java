package com.whisent.kubeloader.compat;

import com.whisent.kubeloader.ConfigManager;
import net.minecraftforge.fml.ModList;

/**
 * GraalJS 兼容性检测工具类
 * 检查 GraalJS 相关依赖是否可用，确保在没有安装 GraalJS 时不出现 ClassNotFoundException
 */
public class GraalJSCompat {
    public static boolean canUseGraalJS = false;
    private static volatile Boolean polyglotAvailable;
    

    public static void init() {
        canUseGraalJS = isRuntimeAvailable();
    }

    public static boolean canUseGraalJS() {
        canUseGraalJS = isRuntimeAvailable() && ConfigManager.shouldUseGraalJS();
        return canUseGraalJS;
    }

    public static boolean isRuntimeAvailable() {
        return isGraalModLoaded() && isPolyglotAvailable();
    }

    public static boolean isGraalModLoaded() {
        try {
            return ModList.get().isLoaded("graalmc");
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isPolyglotAvailable() {
        if (polyglotAvailable == null) {
            synchronized (GraalJSCompat.class) {
                if (polyglotAvailable == null) {
                    try {
                        Class.forName("graal.graalvm.polyglot.Context", false, GraalJSCompat.class.getClassLoader());
                        polyglotAvailable = true;
                    } catch (Throwable ignored) {
                        polyglotAvailable = false;
                    }
                }
            }
        }

        return polyglotAvailable;
    }

    public static String getRuntimeUnavailableReason() {
        if (!isGraalModLoaded()) {
            return "Graal mod is not loaded";
        }

        if (!isPolyglotAvailable()) {
            return "Graal polyglot classes are not available";
        }

        return "Graal runtime is not available";
    }
}