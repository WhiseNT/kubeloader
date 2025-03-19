package com.whisent.kubeloader.impl.dummy;

import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.ContentPackProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author ZZZank
 */
public class DummyContentPackProvider implements ContentPackProvider {
    @NotNull
    private final Collection<? extends @NotNull ContentPack> packs;

    public DummyContentPackProvider(@NotNull Collection<? extends @NotNull ContentPack> packs) {
        this.packs = packs;
    }

    @Override
    public @NotNull Collection<? extends @NotNull ContentPack> providePack() {
        return packs;
    }
}
