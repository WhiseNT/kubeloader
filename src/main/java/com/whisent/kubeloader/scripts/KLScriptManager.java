package com.whisent.kubeloader.scripts;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.graal.context.ContextMap;
import com.whisent.kubeloader.graal.context.IdentifiedContext;
import com.whisent.kubeloader.impl.ContentPackProviders;
import com.whisent.kubeloader.impl.depends.DependencyReport;
import com.whisent.kubeloader.impl.depends.PackDependencyBuilder;
import com.whisent.kubeloader.impl.depends.PackDependencyValidator;
import com.whisent.kubeloader.impl.depends.SortableContentPack;
import com.whisent.kubeloader.utils.topo.TopoNotSolved;
import com.whisent.kubeloader.utils.topo.TopoPreconditionFailed;
import com.whisent.kubeloader.utils.topo.TopoSort;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptPack;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class KLScriptManager {
    public static Map<String, SortableContentPack> injectAndSortPacks(PackLoadingContext context,Map<String, ScriptPack> original) {
        var packs = ContentPackProviders.getPacks();

        var report = validateContentPacks(packs, context);
        if (!report.errors().isEmpty()) {
            // 在有错误发生的时候不加载任何 ContentPack
            return Map.of();
        }

        var indexed = packs.stream().collect(Collectors.toMap(
                ContentPack::id,
                Function.identity()
        ));

        var sortablePacks = new HashMap<String, SortableContentPack>();

        for (var contentPack : packs) {
            Kubeloader.LOGGER.debug("寻找到contentPack: {}", contentPack);
            var scriptPack = contentPack.getPack(context);
            var namespace = contentPack.id();

            IdentifiedContext identifiedContext = new IdentifiedContext(namespace, context.type());
            ContextMap.putContext(identifiedContext);

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
        return Map.copyOf(sortablePacks);
    }
    public static @NotNull DependencyReport validateContentPacks(List<ContentPack> packs, PackLoadingContext context) {
        var validator = new PackDependencyValidator(PackDependencyValidator.DupeHandling.ERROR);
        var report = validator.validate(packs);
        report.infos().stream().map(Component::getString).forEach(context.console()::info);
        report.warnings().stream().map(Component::getString).forEach(context.console()::warn);
        report.errors().stream().map(Component::getString).forEach(context.console()::error);
        return report;
    }
}
