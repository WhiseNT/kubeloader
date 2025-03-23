package com.whisent.kubeloader.cpconfig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.HashMap;
import java.util.Locale;
import java.util.function.Function;

/**
 * @author ZZZank
 */
public class CodecUtil {
    public static <T extends Enum<T>> Codec<T> createEnumStringCodec(Class<T> type) {
        return createEnumStringCodec(type, true);
    }

    public static <T extends Enum<T>> Codec<T> createEnumStringCodec(final Class<T> type, final boolean ignoreCase) {
        var indexedValues = new HashMap<String, T>();
        for (var value : type.getEnumConstants()) {
            var name = value.name();
            if (ignoreCase) {
                name = name.toLowerCase(Locale.ROOT);
            }
            indexedValues.put(name, value);
        }
        return Codec.STRING.comapFlatMap(
            wrapUnsafeFn(name -> {
                if (name == null) {
                    throw new NullPointerException("Name is null");
                }
                var result = indexedValues.get(ignoreCase ? name.toLowerCase(Locale.ROOT) : name);
                if (result == null) {
                    throw new IllegalArgumentException(
                        "No enum constant " + type.getCanonicalName() + "." + name);
                }
                return result;
            }),
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
