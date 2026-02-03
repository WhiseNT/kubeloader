package com.whisent.kubeloader;

import com.whisent.kubeloader.definition.inject.SortablePacksHolder;
import com.whisent.kubeloader.event.KubeLoaderServerEventHandler;
import com.whisent.kubeloader.event.kjs.BlockEntityEvents;
import com.whisent.kubeloader.event.kjs.ItemEntityEvents;
import com.whisent.kubeloader.event.kjs.KubeLoaderEvents;
import com.whisent.kubeloader.klm.dsl.ShadowMixinObject;
import com.whisent.kubeloader.plugin.ContentPacksBinding;
import com.whisent.kubeloader.utils.KLUtil;
import com.whisent.kubeloader.utils.PerformanceUtil;
import com.whisent.kubeloader.utils.modgen.ContentPackGenerator;
import com.whisent.kubeloader.utils.modgen.PackModGenerator;
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
        event.add("KLM", ShadowMixinObject.class);
        if (event.getType() == ScriptType.SERVER) {
            KubeLoaderServerEventHandler.init();
        }

        BlockEntityEvents.GROUP.register();
        ItemEntityEvents.GROUP.register();


    }

    @Override
    public void registerTypeWrappers(ScriptType type, TypeWrappers typeWrappers) {
        super.registerTypeWrappers(type, typeWrappers);
        
        // Example: Register custom type wrappers for Rhino (similar to other mods)
        // typeWrappers.registerSimple(YourCustomClass.class, o -> {
        //     if (o instanceof YourCustomClass custom) return custom;
        //     return YourCustomUtil.fromString(o.toString());
        // });
        
        // Note: KubeLoader also implements GraalJS wrapper mechanism
        // See: com.whisent.kubeloader.graal.wrapper.TypeWrapperRegistry
        // For GraalJS, use TypeWrapperRegistry.registerSimple() in the same way:
        // TypeWrapperRegistry.registerSimple(YourCustomClass.class, o -> {
        //     if (o instanceof YourCustomClass custom) return custom;
        //     return YourCustomUtil.fromString(o.toString());
        // });
        // The GraalJS wrappers are automatically initialized when creating GraalJS contexts
        // and provide similar functionality to Rhino's TypeWrappers for backward compatibility
    }
}
