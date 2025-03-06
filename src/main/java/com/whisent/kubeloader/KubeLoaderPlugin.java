package com.whisent.kubeloader;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.registry.RegistryInfo;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;

import java.util.HashMap;

public class KubeLoaderPlugin extends KubeJSPlugin {
    public static final HashMap<String, Object> PUBLIC = new HashMap<>();

    private static void initializePublicMap() {
        PUBLIC.put("server", new HashMap<>());
        PUBLIC.put("startup", new HashMap<>());
        PUBLIC.put("client", new HashMap<>());
    }

    @Override
    public void init() {
        super.init();

    }

    @Override
    public void registerBindings(BindingsEvent event) {
        event.add("pulic", PUBLIC);
        initializePublicMap();
    }
}
