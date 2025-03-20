package com.whisent.kubeloader.cpconfig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.function.Function;

/**
 * @author ZZZank
 */
public class CodecUtil {
    public static <T extends Enum<T>> Codec<T> createEnumStringCodec(Class<T> type) {
        return Codec.STRING.comapFlatMap(
            wrapUnsafeFn(name -> Enum.valueOf(type, name)),
            Enum::name
        );
    }

    public static <I, O> Function<I, DataResult<O>> wrapUnsafeFn(UnsafeFunction<I, O> function) {
        return function;
    }

    public interface UnsafeFunction<I, O> extends Function<I, DataResult<O>> {
        O applyUnsafe(I input) throws Exception;

        @Override
        default DataResult<O> apply(I i) {
            try {
                return DataResult.success(applyUnsafe(i));
            } catch (Exception e) {
                return DataResult.error(e::toString);
            }
        }
    }
}
