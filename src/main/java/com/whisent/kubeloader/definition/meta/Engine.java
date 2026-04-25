package com.whisent.kubeloader.definition.meta;

import com.mojang.serialization.Codec;
import com.whisent.kubeloader.utils.CodecUtil;

/**
 * @author ZZZank
 */
public enum Engine {
    graaljs,
    rhino,
    default_engine,
    both
    ;

    public static final Codec<Engine> CODEC = CodecUtil.createEnumStringCodec(Engine.class);
}