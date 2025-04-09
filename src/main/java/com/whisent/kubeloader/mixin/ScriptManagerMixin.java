package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.impl.ContentPackProviders;
import com.whisent.kubeloader.impl.depends.PackDependencyBuilder;
import com.whisent.kubeloader.impl.depends.PackDependencyValidator;
import com.whisent.kubeloader.impl.depends.SortableContentPack;
import com.whisent.kubeloader.utils.topo.TopoNotSolved;
import com.whisent.kubeloader.utils.topo.TopoPreconditionFailed;
import com.whisent.kubeloader.utils.topo.TopoSort;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.script.*;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Mixin(ScriptManager.class)
public abstract class ScriptManagerMixin {

    @Redirect(method = "load", at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;"))
    private Collection<ScriptPack> injectPacks(Map<String, ScriptPack> original) {
        var context = new PackLoadingContext(thiz());
        var packs = ContentPackProviders.getPacks();

        var validator = new PackDependencyValidator(PackDependencyValidator.DupeHandling.ERROR);
        var report = validator.validate(packs);
        report.infos().stream().map(Component::getString).forEach(context.console()::info);
        report.warnings().stream().map(Component::getString).forEach(context.console()::warn);
        report.errors().stream().map(Component::getString).forEach(context.console()::error);

        if (!report.errors().isEmpty()) {
            // 在有错误发生的时候不加载任何 ContentPack
            return original.values();
        }

        var merged = new HashMap<>(original);
        for (var contentPack : packs) {
            Kubeloader.LOGGER.debug("寻找到contentPack: {}", contentPack);
            var pack = contentPack.getPack(context);
            if (pack != null) {
                merged.put(contentPack.getNamespace(context), contentPack.postProcessPack(context, pack));
            }
        }

        var indexed = packs.stream()
            .collect(Collectors.toMap(ContentPack::getNamespace, Function.identity()));

        var sortablePacks = new ArrayList<SortableContentPack>();
        for (var entry : merged.entrySet()) {
            // redirect xxxx_scripts to kubejs
            var id = context.folderName().equals(entry.getKey()) ? KubeJS.MOD_ID : entry.getKey();
            var scriptPack = entry.getValue();
            var pack = indexed.get(id);
            if (pack == null) {
                pack = indexed.get(KubeJS.MOD_ID);
            }
            sortablePacks.add(new SortableContentPack(id, pack, scriptPack));
        }

        var dependencyBuilder = new PackDependencyBuilder();
        dependencyBuilder.build(sortablePacks);

        try {
            return TopoSort.sort(sortablePacks)
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

    @Unique
    private ScriptManager thiz() {
        return (ScriptManager) (Object) this;
    }
}
