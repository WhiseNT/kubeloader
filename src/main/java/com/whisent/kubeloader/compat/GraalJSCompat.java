package com.whisent.kubeloader.compat;

import com.whisent.kubeloader.ConfigManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * GraalJS 兼容性检测工具类
 * 检查 GraalJS 相关依赖是否可用，确保在没有安装 GraalJS 时不出现 ClassNotFoundException
 */
public class GraalJSCompat {
    public static boolean canUseGraalJS = false;
    

    public static void init() {

        canUseGraalJS = true;
    }
    public static boolean canUseGraalJS() {
        canUseGraalJS = ConfigManager.shouldUseGraalJS();
        return canUseGraalJS;
    }
}