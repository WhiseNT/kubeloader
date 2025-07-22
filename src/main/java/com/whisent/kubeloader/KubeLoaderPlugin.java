package com.whisent.kubeloader;

import com.whisent.kubeloader.definition.inject.SortablePacksHolder;
import com.whisent.kubeloader.files.FileIO;
import com.whisent.kubeloader.item.NbtItemBuilder;
import com.whisent.kubeloader.item.dynamic.DynamicTieredItemBuilder;
import com.whisent.kubeloader.item.vanilla.bow.BowItemBuilder;
import com.whisent.kubeloader.plugin.ContentPacksBinding;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.registry.RegistryInfo;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import net.minecraft.core.registries.Registries;

public class KubeLoaderPlugin extends KubeJSPlugin {
    @Override
    public void init() {
        //RegistryInfo.ITEM.addType("nbt", NbtItemBuilder.class, NbtItemBuilder::new);
        //RegistryInfo.ITEM.addType("dynamic_sword", DynamicSwordBuilder.class,DynamicSwordBuilder::new);
        //RegistryInfo.ITEM.addType("dynamic_pickaxe", DynamicPickAxeBuilder.class, DynamicPickAxeBuilder::new);
        //RegistryInfo.ITEM.addType("dynamic_main", DynamicTieredItemBuilder.class, DynamicTieredItemBuilder::new);
        RegistryInfo.ITEM.addType("bow", BowItemBuilder.class, BowItemBuilder::new);
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        var packsHolder = (SortablePacksHolder) event.manager;
        event.add("ContentPacks", new ContentPacksBinding(event.getType(), packsHolder));
        event.add("Registries", Registries.class);
    }


}
