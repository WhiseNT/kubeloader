package com.whisent.kubeloader.definition;

import org.jetbrains.annotations.Nullable;

/**
 * @author ZZZank
 */
public interface ContentPackProvider {

    /**
     * @return 该 ContentPackProvider 所提供的 ContentPack，或者，如果其没有，则返回 {@code null}
     */
    @Nullable
    ContentPack providePack();
}
