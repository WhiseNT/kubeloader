package com.whisent.kubeloader;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;

import java.util.HashMap;

public class KubeLoaderPlugin extends KubeJSPlugin {
    public static final HashMap<String, Object> STARTUPFIELD = new HashMap<>();
    public static final HashMap<String, Object> SERVERFIELD = new HashMap<>();
    public static final HashMap<String, Object> CLIENTFIELD = new HashMap<>();


    @Override
    public void init() {
        super.init();

    }
    @Override
    public void registerBindings(BindingsEvent event) {
        switch (event.getType()) {
            case STARTUP :
                event.add("startupField", STARTUPFIELD);
            case SERVER :
                event.add("serverField", SERVERFIELD);
            case CLIENT :
                event.add("clientField", CLIENTFIELD);
        }
    }
}
