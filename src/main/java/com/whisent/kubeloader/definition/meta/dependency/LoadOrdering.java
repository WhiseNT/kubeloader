package com.whisent.kubeloader.definition.meta.dependency;

import com.mojang.serialization.Codec;
import com.whisent.kubeloader.utils.CodecUtil;

/**
 * @author ZZZank
 */
public enum LoadOrdering {
    NONE,
    BEFORE,
    AFTER,
    ;

    public static final Codec<LoadOrdering> CODEC = CodecUtil.createEnumStringCodec(LoadOrdering.class);
}
