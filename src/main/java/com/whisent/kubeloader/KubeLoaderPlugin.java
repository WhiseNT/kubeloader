package com.whisent.kubeloader;

import com.whisent.kubeloader.impl.ContentPackProviders;
import com.whisent.kubeloader.impl.path.PathContentPack;
import com.whisent.kubeloader.impl.zip.ZipContentPack;
import com.whisent.kubeloader.item.NbtItemBuilder;
import com.whisent.kubeloader.item.dynamic.DynamicPickAxeBuilder;
import com.whisent.kubeloader.item.dynamic.DynamicSwordBuilder;
import com.whisent.kubeloader.item.dynamic.DynamicTieredItemBuilder;
import com.whisent.kubeloader.item.vanilla.bow.BowItemBuilder;
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
        //RegistryInfo.ITEM.addType("dynamic_sword", DynamicSwordBuilder.class,DynamicSwordBuilder::new);
        //.ITEM.addType("dynamic_pickaxe", DynamicPickAxeBuilder.class, DynamicPickAxeBuilder::new);
        //RegistryInfo.ITEM.addType("dynamic_main", DynamicTieredItemBuilder.class, DynamicTieredItemBuilder::new);
        RegistryInfo.ITEM.addType("bow", BowItemBuilder.class, BowItemBuilder::new);
    }
    @Override
    public void registerBindings(BindingsEvent event) {

        ContentPackProviders.getPacks().stream()
                .filter(e -> e instanceof ZipContentPack || e instanceof PathContentPack)
                .forEach(pack -> {
                    switch (event.getType()) {
                        case STARTUP :
                            STARTUPFIELD.put(pack.getNamespace(), pack.getMetaData());
                        case SERVER :
                            SERVERFIELD.put(pack.getNamespace(), pack.getMetaData());
                        case CLIENT :
                            CLIENTFIELD.put(pack.getNamespace(), pack.getMetaData());
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
