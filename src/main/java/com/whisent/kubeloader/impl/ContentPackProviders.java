package com.whisent.kubeloader.impl;

import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.ContentPackProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author ZZZank
 */
public final class ContentPackProviders {
    private static final List<ContentPackProvider> PROVIDERS = new ArrayList<>();
    private static List<ContentPack> cachedPacks = null;

    public static void register(ContentPackProvider... providers) {
        for (var provider : providers) {
            PROVIDERS.add(Objects.requireNonNull(provider));
        }
    }

    public static List<ContentPackProvider> getProviders() {
        return Collections.unmodifiableList(PROVIDERS);
    }

    public static List<ContentPack> getPacks() {
        if (cachedPacks == null) {
            cachedPacks = PROVIDERS
                .stream()
                .map(ContentPackProvider::providePack)
                .filter(Objects::nonNull)
                .toList();
        }
        return cachedPacks;
    }
}
