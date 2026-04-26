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
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.script.TypeWrapperRegistry;
import net.minecraft.core.registries.Registries;

public class KubeLoaderPlugin implements KubeJSPlugin {


    @Override
    public void init() {
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(KubeLoaderEvents.GROUP);
        registry.register(BlockEntityEvents.GROUP);
        registry.register(ItemEntityEvents.GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry event) {
        var packsHolder = (SortablePacksHolder) event.context().kjsFactory.manager;
        event.add("ContentPacks", new ContentPacksBinding(event.type(), packsHolder));
        event.add("Registries", Registries.class);
        event.add("Perf", new PerformanceUtil(event.type()));
        event.add("KLUtils", KLUtil.class);
        event.add("ModGen", PackModGenerator.class);
        event.add("PackGen", ContentPackGenerator.class);
        event.add("KLM", ShadowMixinObject.class);
        if (event.type() == ScriptType.SERVER) {
            KubeLoaderServerEventHandler.init();
        }
    }

    @Override
    public void registerTypeWrappers(TypeWrapperRegistry registry) {
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
