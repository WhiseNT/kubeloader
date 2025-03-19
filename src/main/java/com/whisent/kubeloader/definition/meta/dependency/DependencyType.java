package com.whisent.kubeloader.definition.meta.dependency;

import com.mojang.serialization.Codec;
import com.whisent.kubeloader.cpconfig.CodecUtil;

/**
 * @author ZZZank
 */
public enum DependencyType {
    REQUIRED, // 需要才能运行的依赖，否则崩溃。
    OPTIONAL, // 不必要就能运行的依赖，否则输出警告。
    RECOMMENDED, // 不需要就能运行的依赖，用作元数据。
    DISCOURAGED, // 一起运行时可能出现问题的模组。一起运行时，输出警告。
    INCOMPATIBLE, // 一起运行可能导致崩溃的模组。一起运行时崩溃。
    ;

    public static final Codec<DependencyType> CODEC = CodecUtil.createEnumStringCodec(DependencyType.class);
}
