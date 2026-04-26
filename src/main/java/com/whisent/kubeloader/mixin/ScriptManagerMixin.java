package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.ConfigManager;
import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.compat.GraalJSCompat;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.definition.inject.SortablePacksHolder;
import com.whisent.kubeloader.graal.GraalScriptManager;
import com.whisent.kubeloader.impl.CommonScriptsLoader;
import com.whisent.kubeloader.impl.ContentPackProviders;
import com.whisent.kubeloader.impl.depends.DependencyReport;
import com.whisent.kubeloader.impl.depends.PackDependencyBuilder;
import com.whisent.kubeloader.impl.depends.PackDependencyValidator;
import com.whisent.kubeloader.impl.depends.SortableContentPack;
import com.whisent.kubeloader.impl.mixin.ScriptManagerInterface;
import com.whisent.kubeloader.klm.MixinManager;
import com.whisent.kubeloader.klm.dsl.MixinDSL;
import com.whisent.kubeloader.utils.topo.TopoNotSolved;
import com.whisent.kubeloader.utils.topo.TopoPreconditionFailed;
import com.whisent.kubeloader.utils.topo.TopoSort;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.script.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Mixin(value = ScriptManager.class,remap = false)
public abstract class ScriptManagerMixin implements SortablePacksHolder, ScriptManagerInterface,AccessScriptManager {

    @Shadow @Final public Map<String, ScriptPack> packs;

    @Shadow @Final public ScriptType scriptType;
    @Unique
    private Map<String, SortableContentPack> kubeLoader$sortablePacks;

    @Unique
    private Map<String, List<MixinDSL>> kubeLoader$mixinDSLs = new HashMap<>();

    @Unique
    public Map<String, Object> kubeLoader$scriptContexts = new HashMap<>();

    @Unique
    public Map<String,Object> kubeLoader$bindings = new HashMap<>();
    @Redirect(method = "load", at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;"))
    private Collection<ScriptPack> injectPacks(Map<String, ScriptPack> original) {

        //清空mixin数据
        getKubeLoader$mixinDSLs().clear();

        var context = new PackLoadingContext((ScriptManager) (Object) this);
        var packs = ContentPackProviders.getPacks();

        var report = kubeLoader$validateContentPacks(packs, context);
        if (!report.errors().isEmpty()) {
            // 在有错误发生的时候不加载任何 ContentPack
            return original.values();
        }

        var indexed = packs.stream().collect(Collectors.toMap(
                ContentPack::id,
                Function.identity()
        ));



        var sortablePacks = new HashMap<String, SortableContentPack>();
        thiz().scriptType.console.log("[KubeLoader] JS Engine: "+ ConfigManager.getConfig().getEngine());
        for (var contentPack : packs) {
            Kubeloader.LOGGER.debug("寻找到contentPack: {}", contentPack);
            var scriptPack = contentPack.getPack(context);

            var namespace = contentPack.id();
            if (GraalJSCompat.canUseGraalJS()) {
                GraalScriptManager.loadContentPack(this, scriptType, scriptPack, namespace);
            }

            List<ScriptPack> scriptPacks;
            if (KubeJS.MOD_ID.equals(namespace)) {
                System.out.println("[KubeLoader] Detected KubeJS's own content pack. Loading all script packs from original map.");
                if (GraalJSCompat.canUseGraalJS()) {
                    System.out.println("[KubeLoader] Setting context for all original script packs.");
                    for (ScriptPack pack : original.values()) {
                        String packNamespace = pack.info.namespace;
                        GraalScriptManager.loadScriptPack(this, packNamespace);
                    }
                }
                scriptPacks = original.values().stream()
                    .filter(p -> !indexed.containsKey(p.info.namespace))
                    .toList();
                if (GraalJSCompat.canUseGraalJS()) {
                    for (ScriptPack pack : scriptPacks) {
                        System.out.println("[KubeLoader] Setting context for all original script packs.");
                        GraalScriptManager.setContextForPack(this, pack);
                    }
                }
            } else if (scriptPack != null) {
                scriptPacks = List.of(contentPack.postProcessPack(context, scriptPack));
            } else {
                scriptPacks = List.of();
            }

            var sortable = new SortableContentPack(
                    namespace,
                    contentPack,
                    scriptPacks
            );
            sortablePacks.put(namespace, sortable);
        }

        kubeLoader$sortablePacks = Map.copyOf(sortablePacks);

        var dependencyBuilder = new PackDependencyBuilder();
        dependencyBuilder.build(sortablePacks.values());

        try {
            return TopoSort.sort(sortablePacks.values())
                    .stream()
                    .map(SortableContentPack::scriptPacks)
                    .flatMap(Collection::stream)
                    .toList();
        } catch (TopoNotSolved | TopoPreconditionFailed e) {
            context.console().error(e);
            // TODO: 决定是否要在有错误发生的时候 不 加载 ContentPack
            return original.values();
        }
    }

    @Inject(method = "loadFromDirectory", at = @At("HEAD"))
    private void injectContentPacks(CallbackInfo ci) {
        if (thiz().scriptType.isStartup() || ServerLifecycleHooks.getCurrentServer() != null) {
            MixinManager.getMixinMap().clear();
            MixinManager.loadMixins(Kubeloader.MixinPath,"");
        }
    }


    @Inject(method = "loadPackFromDirectory", at = @At("TAIL"))
    private void loadCommonScripts(Path path, String name, boolean exampleFile, CallbackInfo ci) {
        ScriptPack pack = this.packs.get(path.getFileName().toString());
        if (pack != null) {
            CommonScriptsLoader.loadCommonScripts(thiz(), pack, thiz().scriptType.path.getParent(), "common_scripts");
        }

    }

    @Override
    public Map<String, SortableContentPack> kubeLoader$sortablePacks() {
        return kubeLoader$sortablePacks;
    }

    @Unique
    private static @NotNull DependencyReport kubeLoader$validateContentPacks(List<ContentPack> packs, PackLoadingContext context) {
        var validator = new PackDependencyValidator(PackDependencyValidator.DupeHandling.ERROR);
        var report = validator.validate(packs);
        report.infos().stream().map(Component::getString).forEach(context.console()::info);
        report.warnings().stream().map(Component::getString).forEach(context.console()::warn);
        report.errors().stream().map(Component::getString).forEach(context.console()::error);
        return report;
    }

    public Map<String, List<MixinDSL>> getKubeLoader$mixinDSLs() {
        return kubeLoader$mixinDSLs;
    }

    public Map<String, Object> getKubeLoader$scriptContexts() {
        return this.kubeLoader$scriptContexts;
    }


    public Map<String,Object> getKubeLoader$bindings() {
        return this.kubeLoader$bindings;
    }

    public ScriptManager thiz() {
        return (ScriptManager) (Object) this;
    }
}



