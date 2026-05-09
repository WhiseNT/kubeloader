package com.whisent.kubeloader.scripts.tserase;

import java.util.ArrayList;
import java.util.List;

public class TsErasePluginManager {
    public static final TsErasePluginManager INSTANCE = new TsErasePluginManager();

    private final List<TsErasePlugin> plugins = new ArrayList<>();

    private TsErasePluginManager() {
        plugins.add(new TsEraseStandaloneTypeDeclarationPlugin());
        plugins.add(new TsEraseImplementsClausePlugin());
        plugins.add(new TsEraseGenericTypePlugin());
        plugins.add(new TsEraseTypeAnnotationPlugin());
    }

    public boolean applyCurrent(TsEraseContext ctx) {
        for (TsErasePlugin p : plugins) {
            if (p.matches(ctx)) {
                return p.apply(ctx);
            }
        }
        return false;
    }
}

