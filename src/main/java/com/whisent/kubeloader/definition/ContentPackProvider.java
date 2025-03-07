package com.whisent.kubeloader.definition;

import org.jetbrains.annotations.Nullable;

/**
 * @author ZZZank
 */
public interface ContentPackProvider {

    /**
     * 所谓“动态”也就是每次调用{@link #providePack()}的时候，可能会返回不同的结果。非动态的ContentPackProvider返回的结果
     * 会被缓存以免去重复收集
     * <p>
     * 这里假定如无特地更改，ContentPackProvider都是动态的
     */
    default boolean isDynamic() {
        return true;
    }

    /**
     * @return 该 ContentPackProvider 所提供的 ContentPack，或者，如果其没有，则返回 {@code null}
     */
    @Nullable
    ContentPack providePack();
}
