package com.whisent.kubeloader.definition.meta.dependency;

import com.mojang.serialization.Codec;
import com.whisent.kubeloader.cpconfig.CodecUtil;

/**
 * @author ZZZank
 */
public enum DependencyType {
    /**
     * 需要才能运行的依赖，否则崩溃。
     */
    REQUIRED,
    /**
     * 不必要就能运行的依赖，不存在时不会崩溃，但是仍然参与依赖排序。
     */
    OPTIONAL,
    /**
     * 不需要就能运行的依赖，用作元数据。
     */
    RECOMMENDED,
    /**
     * 一起运行时可能出现问题的模组。一起运行时，输出警告。
     */
    DISCOURAGED,
    /**
     * 一起运行可能导致崩溃。发现时打断加载。
     */
    INCOMPATIBLE,
    ;

    public static final Codec<DependencyType> CODEC = CodecUtil.createEnumStringCodec(DependencyType.class);
}
