package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.impl.ContentPackProviders;
import com.whisent.kubeloader.impl.depends.PackDependencyBuilder;
import com.whisent.kubeloader.impl.depends.PackDependencyValidator;
import com.whisent.kubeloader.impl.depends.SortableContentPack;
import com.whisent.kubeloader.impl.path.PathContentPack;
import com.whisent.kubeloader.impl.zip.ZipContentPack;
import com.whisent.kubeloader.utils.topo.TopoNotSolved;
import com.whisent.kubeloader.utils.topo.TopoPreconditionFailed;
import com.whisent.kubeloader.utils.topo.TopoSort;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.script.*;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Mixin(ScriptManager.class)
public abstract class ScriptManagerMixin {

    @Shadow
    @Final
    public Map<String, ScriptPack> packs;

    @Shadow
    @Final
    public ScriptType scriptType;

    @Unique
    private List<ContentPack> kl$contentPacks;

    @Inject(method = "reload", at = @At(value = "INVOKE", target = "Ldev/latvian/mods/kubejs/script/ScriptManager;load()V"), remap = false)
    private void injectPacks(CallbackInfo ci) {
        var context = new PackLoadingContext(thiz());
        var packs = ContentPackProviders.getPacks();

        var validator = new PackDependencyValidator(PackDependencyValidator.DupeHandling.ERROR);
        var report = validator.validate(packs);
        report.infos().stream().map(Component::getString).forEach(context.console()::info);
        report.warnings().stream().map(Component::getString).forEach(context.console()::warn);
        // TODO: 决定是否要在有错误发生的时候 不 加载 ContentPack
        report.errors().stream().map(Component::getString).forEach(context.console()::error);

        for (var contentPack : packs) {
            Kubeloader.LOGGER.debug("寻找到contentPack: {}", contentPack);
            var pack = contentPack.getPack(context);
            if (true) {
                Kubeloader.LOGGER.debug("寻找到MetaData"+contentPack.getMetaData());

            }

            if (pack != null) {
                this.packs.put(contentPack.getNamespace(), pack);
            }
        }

        kl$contentPacks = packs;
    }

    @Redirect(method = "load", at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;"), remap = false)
    private Collection<ScriptPack> sortPack(Map<String, ScriptPack> scriptPackMap) {
        var indexed = this.kl$contentPacks.stream()
            .collect(Collectors.toMap(ContentPack::getNamespace, Function.identity()));
        var kjsName = PackLoadingContext.folderName(scriptType);

        var sortablePacks = new ArrayList<SortableContentPack>();
        for (var entry : scriptPackMap.entrySet()) {
            var id = kjsName.equals(entry.getKey()) ? KubeJS.MOD_ID : entry.getKey();
            var scriptPack = entry.getValue();
            var pack = indexed.get(id);
            sortablePacks.add(new SortableContentPack(id, pack, scriptPack));
        }

        var dependencyBuilder = new PackDependencyBuilder();
        dependencyBuilder.build(sortablePacks);

        try {
            return TopoSort.sort(sortablePacks)
                .stream()
                .map(SortableContentPack::scriptPack)
                .toList();
        } catch (TopoNotSolved | TopoPreconditionFailed e) {
            scriptType.console.error(e);
            return scriptPackMap.values();
        }
    }

    @Unique
    private ScriptManager thiz() {
        return (ScriptManager) (Object) this;
    }
}
