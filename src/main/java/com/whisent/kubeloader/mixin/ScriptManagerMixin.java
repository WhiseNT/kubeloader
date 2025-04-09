package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.definition.inject.SortablePacksHolder;
import com.whisent.kubeloader.impl.ContentPackProviders;
import com.whisent.kubeloader.impl.depends.DependencyReport;
import com.whisent.kubeloader.impl.depends.PackDependencyBuilder;
import com.whisent.kubeloader.impl.depends.PackDependencyValidator;
import com.whisent.kubeloader.impl.depends.SortableContentPack;
import com.whisent.kubeloader.utils.topo.TopoNotSolved;
import com.whisent.kubeloader.utils.topo.TopoPreconditionFailed;
import com.whisent.kubeloader.utils.topo.TopoSort;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.script.*;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Mixin(ScriptManager.class)
public abstract class ScriptManagerMixin implements SortablePacksHolder {

    @Unique
    private Map<String, SortableContentPack> kubeLoader$sortablePacks;

    @Redirect(method = "load", at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;"))
    private Collection<ScriptPack> injectPacks(Map<String, ScriptPack> original) {
        var context = new PackLoadingContext((ScriptManager) (Object) this);
        var packs = ContentPackProviders.getPacks();

        var report = kubeloader$validateContentPacks(packs, context);
        if (!report.errors().isEmpty()) {
            // 在有错误发生的时候不加载任何 ContentPack
            return original.values();
        }

        var indexed = packs.stream().collect(Collectors.toMap(
            ContentPack::getNamespace,
            Function.identity()
        ));

        var sortablePacks = new HashMap<String, SortableContentPack>();

        for (var contentPack : packs) {
            Kubeloader.LOGGER.debug("寻找到contentPack: {}", contentPack);
            var scriptPack = contentPack.getPack(context);
            var namespace = contentPack.getNamespace(context);

            List<ScriptPack> scriptPacks;
            if (KubeJS.MOD_ID.equals(namespace)) {
                scriptPacks = original
                    .values()
                    .stream()
                    .filter(p -> !indexed.containsKey(p.info.namespace))
                    .toList();
            } else if (scriptPack != null) {
                scriptPacks = List.of(contentPack.postProcessPack(context, scriptPack));
            } else {
                // 空的，可以被诸如没有xxxx_scirpts文件夹之类的情况出发，此时仍然参与排序
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

    @Override
    public Map<String, SortableContentPack> kubeLoader$sortablePacks() {
        return kubeLoader$sortablePacks;
    }

    @Unique
    private static @NotNull DependencyReport kubeloader$validateContentPacks(List<ContentPack> packs, PackLoadingContext context) {
        var validator = new PackDependencyValidator(PackDependencyValidator.DupeHandling.ERROR);
        var report = validator.validate(packs);
        report.infos().stream().map(Component::getString).forEach(context.console()::info);
        report.warnings().stream().map(Component::getString).forEach(context.console()::warn);
        report.errors().stream().map(Component::getString).forEach(context.console()::error);
        return report;
    }
}
