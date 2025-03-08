package com.whisent.kubeloader.impl;

import com.whisent.kubeloader.Kubeloader;
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
    private static List<ContentPack> cachedPacks = null;
    private static final List<ContentPackProvider> STATIC_PROVIDERS = new ArrayList<>();
    private static final List<ContentPackProvider> DYNAMIC_PROVIDERS = new ArrayList<>();

    public static void register(ContentPackProvider... providers) {
        for (var provider : providers) {
            if (provider.isDynamic()) {
                Kubeloader.LOGGER.debug("增加了动态Provider"+provider);
                DYNAMIC_PROVIDERS.add(provider);
            } else {
                STATIC_PROVIDERS.add(provider);
            }
        }
    }

    public static List<ContentPackProvider> getDynamicProviders() {
        return Collections.unmodifiableList(DYNAMIC_PROVIDERS);
    }

    public static List<ContentPackProvider> getStaticProviders() {
        return Collections.unmodifiableList(STATIC_PROVIDERS);
    }

    public static List<ContentPack> getPacks() {
        if (cachedPacks == null) {
            cachedPacks = STATIC_PROVIDERS
                .stream()
                .map(ContentPackProvider::providePack)
                .filter(Objects::nonNull)
                .toList();
        }
        var packs = new ArrayList<>(cachedPacks);
        for (var provider : DYNAMIC_PROVIDERS) {
            Kubeloader.LOGGER.debug("尝试添加Pack"+provider);
            packs.add(provider.providePack());
        }
        return packs;
    }
}
