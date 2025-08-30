package com.whisent.kubeloader;

import com.whisent.kubeloader.definition.inject.SortablePacksHolder;
import com.whisent.kubeloader.event.KubeLoaderServerEventHandler;
import com.whisent.kubeloader.event.kjs.BlockEntityEvents;
import com.whisent.kubeloader.event.kjs.ItemEntityEvents;
import com.whisent.kubeloader.event.kjs.KubeLoaderEvents;
import com.whisent.kubeloader.item.vanilla.bow.BowItemBuilder;
import com.whisent.kubeloader.mixinjs.dsl.ShadowMixinObject;
import com.whisent.kubeloader.plugin.ContentPacksBinding;
import com.whisent.kubeloader.utils.KLUtil;
import com.whisent.kubeloader.utils.PerformanceUtil;
import com.whisent.kubeloader.utils.mod_gen.ContentPackGenerator;
import com.whisent.kubeloader.utils.mod_gen.PackModGenerator;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.registry.RegistryInfo;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.rhino.util.wrap.TypeWrappers;
import net.minecraft.core.registries.Registries;

public class KubeLoaderPlugin extends KubeJSPlugin {


    @Override
    public void init() {
        //RegistryInfo.ITEM.addType("nbt", NbtItemBuilder.class, NbtItemBuilder::new);
        //RegistryInfo.ITEM.addType("dynamic_sword", DynamicSwordBuilder.class,DynamicSwordBuilder::new);
        //RegistryInfo.ITEM.addType("dynamic_pickaxe", DynamicPickAxeBuilder.class, DynamicPickAxeBuilder::new);
        //RegistryInfo.ITEM.addType("dynamic_main", DynamicTieredItemBuilder.class, DynamicTieredItemBuilder::new);
        RegistryInfo.ITEM.addType("bow", BowItemBuilder.class, BowItemBuilder::new);
        KubeLoaderEvents.GROUP.register();
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        var packsHolder = (SortablePacksHolder) event.manager;
        event.add("ContentPacks", new ContentPacksBinding(event.getType(), packsHolder));
        event.add("Registries", Registries.class);
        event.add("Perf",new PerformanceUtil(event.getType()));
        event.add("KLUtils", KLUtil.class);
        event.add("ModGen", PackModGenerator.class);
        event.add("PackGen", ContentPackGenerator.class);
        event.add("Mixin", ShadowMixinObject.class);
        if (event.getType() == ScriptType.SERVER) {
            KubeLoaderServerEventHandler.init();
        }

        BlockEntityEvents.GROUP.register();
        ItemEntityEvents.GROUP.register();


    }

    @Override
    public void registerTypeWrappers(ScriptType type, TypeWrappers typeWrappers) {
        super.registerTypeWrappers(type, typeWrappers);

    }
}
