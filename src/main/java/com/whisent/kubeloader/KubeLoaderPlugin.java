package com.whisent.kubeloader;

import com.whisent.kubeloader.impl.ContentPackProviders;
import com.whisent.kubeloader.impl.path.PathContentPack;
import com.whisent.kubeloader.impl.zip.ZipContentPack;
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

        ContentPackProviders.getPacks().stream()
                .filter(e -> e instanceof ZipContentPack || e instanceof PathContentPack)
                .forEach(pack -> {
                    if (pack instanceof ZipContentPack) {
                        Kubeloader.LOGGER.debug("内容包集合："+pack.getNamespace());
                        STARTUPFIELD.put(pack.getNamespace(), ((ZipContentPack) pack).getConfig());
                        SERVERFIELD.put(pack.getNamespace(), ((ZipContentPack) pack).getConfig());
                        CLIENTFIELD.put(pack.getNamespace(), ((ZipContentPack) pack).getConfig());
                    }
                    if (pack instanceof PathContentPack) {
                        STARTUPFIELD.put(pack.getNamespace(), ((PathContentPack) pack).getConfig());
                        SERVERFIELD.put(pack.getNamespace(), ((PathContentPack) pack).getConfig());
                        CLIENTFIELD.put(pack.getNamespace(), ((PathContentPack) pack).getConfig());
                    }

                });
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
