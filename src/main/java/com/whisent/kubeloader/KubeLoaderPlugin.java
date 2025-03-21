package com.whisent.kubeloader;

import com.whisent.kubeloader.impl.ContentPackProviders;
import com.whisent.kubeloader.impl.path.PathContentPack;
import com.whisent.kubeloader.impl.zip.ZipContentPack;
import com.whisent.kubeloader.item.NbtItemBuilder;
import com.whisent.kubeloader.item.dynamic.DynamicPickAxeBuilder;
import com.whisent.kubeloader.item.dynamic.DynamicPickAxeItem;
import com.whisent.kubeloader.item.dynamic.DynamicSwordBuilder;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.registry.RegistryInfo;
import dev.latvian.mods.kubejs.script.BindingsEvent;

import java.util.HashMap;

public class KubeLoaderPlugin extends KubeJSPlugin {
    public static final HashMap<String, Object> STARTUPFIELD = new HashMap<>();
    public static final HashMap<String, Object> SERVERFIELD = new HashMap<>();
    public static final HashMap<String, Object> CLIENTFIELD = new HashMap<>();


    @Override
    public void init() {
        RegistryInfo.ITEM.addType("nbt", NbtItemBuilder.class,NbtItemBuilder::new);
        RegistryInfo.ITEM.addType("dynamic_sword", DynamicSwordBuilder.class,DynamicSwordBuilder::new);
        RegistryInfo.ITEM.addType("dynamic_pickaxe", DynamicPickAxeBuilder.class, DynamicPickAxeBuilder::new);
    }
    @Override
    public void registerBindings(BindingsEvent event) {

        ContentPackProviders.getPacks().stream()
                .filter(e -> e instanceof ZipContentPack || e instanceof PathContentPack)
                .forEach(pack -> {
                    if (pack instanceof ZipContentPack) {
                        Kubeloader.LOGGER.debug("内容包集合："+pack.getNamespace());
                        switch (event.getType()) {
                            case STARTUP :
                                STARTUPFIELD.put(pack.getNamespace(), pack.getConfig());
                            case SERVER :
                                SERVERFIELD.put(pack.getNamespace(),pack.getConfig());
                            case CLIENT :
                                CLIENTFIELD.put(pack.getNamespace(), pack.getConfig());
                        }
                    }
                    if (pack instanceof PathContentPack) {
                        switch (event.getType()) {
                            case STARTUP :
                                STARTUPFIELD.put(pack.getNamespace(), pack.getConfig());
                            case SERVER :
                                SERVERFIELD.put(pack.getNamespace(), pack.getConfig());
                            case CLIENT :
                                CLIENTFIELD.put(pack.getNamespace(), pack.getConfig());
                        }
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
