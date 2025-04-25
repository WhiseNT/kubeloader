package com.whisent.kubeloader.definition.meta.dependency;

import com.mojang.serialization.Codec;
import com.whisent.kubeloader.utils.CodecUtil;

/**
 * @author ZZZank
 */
public enum DependencySource {
    PACK,
    MOD
    ;

    public static final Codec<DependencySource> CODEC = CodecUtil.createEnumStringCodec(DependencySource.class);
}
