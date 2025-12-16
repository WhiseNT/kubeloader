package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.definition.inject.SortablePacksHolder;

import com.whisent.kubeloader.graal.DynamicGraalConsole;
import com.whisent.kubeloader.graal.GraalApi;
import com.whisent.kubeloader.graal.context.ContextMap;
import com.whisent.kubeloader.graal.context.IdentifiedContext;
import com.whisent.kubeloader.impl.CommonScriptsLoader;
import com.whisent.kubeloader.impl.ContentPackProviders;
import com.whisent.kubeloader.impl.depends.DependencyReport;
import com.whisent.kubeloader.impl.depends.PackDependencyBuilder;
import com.whisent.kubeloader.impl.depends.PackDependencyValidator;
import com.whisent.kubeloader.impl.depends.SortableContentPack;
import com.whisent.kubeloader.impl.mixin.GraalPack;
import com.whisent.kubeloader.impl.mixin.ScriptFileInfoInterface;
import com.whisent.kubeloader.impl.mixin.ScriptManagerInterface;
import com.whisent.kubeloader.klm.MixinManager;
import com.whisent.kubeloader.klm.dsl.MixinDSL;
import com.whisent.kubeloader.utils.topo.TopoNotSolved;
import com.whisent.kubeloader.utils.topo.TopoPreconditionFailed;
import com.whisent.kubeloader.utils.topo.TopoSort;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.script.*;
import dev.latvian.mods.kubejs.util.UtilsJS;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import org.graalvm.polyglot.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

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
    public Map<String, Context> kubeLoader$scriptContexts = new HashMap<>();

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
        System.out.println("触发"+scriptType);
        for (var contentPack : packs) {
            Kubeloader.LOGGER.debug("寻找到contentPack: {}", contentPack);
            var scriptPack = contentPack.getPack(context);
            System.out.println("测试"+scriptType);

            var namespace = contentPack.id();
            /*
            if (kubeLoader$scriptContexts.get(namespace) == null) {
                kubeLoader$scriptContexts.put(namespace, GraalApi.createContext());
            } else {
                kubeLoader$scriptContexts.get(namespace).close();
                kubeLoader$scriptContexts.put(namespace, GraalApi.createContext());
            }
            if (scriptPack != null) {
                var graalPack = ((GraalPack)scriptPack);
                graalPack.kubeLoader$setGraalContext(kubeLoader$scriptContexts.get(namespace));
                graalPack.kubeLoader$setDynamicGraalConsole(new DynamicGraalConsole(thiz().scriptType.console));
            }
            */

            List<ScriptPack> scriptPacks;

            if (KubeJS.MOD_ID.equals(namespace)) {
                /*
                if (kubeLoader$scriptContexts.get(thiz().scriptType.name) == null) {
                    kubeLoader$scriptContexts.put(thiz().scriptType.name,
                            GraalApi.createContext(thiz()));
                } else {
                    kubeLoader$scriptContexts.get(thiz().scriptType.name).close();
                    kubeLoader$scriptContexts.put(thiz().scriptType.name,
                            GraalApi.createContext(thiz()));
                }

                 */
                scriptPacks = original
                    .values()
                    .stream()
                    .filter(p -> !indexed.containsKey(p.info.namespace))
                    .toList();
                /*
                scriptPacks.forEach(p -> {
                    var graalPack = ((GraalPack)p);
                    graalPack.kubeLoader$setGraalContext(kubeLoader$scriptContexts.get(thiz().scriptType.name));
                    graalPack.kubeLoader$setDynamicGraalConsole(new DynamicGraalConsole(thiz().scriptType.console));
                });

                 */
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

    @Inject(method = "loadFile", at = @At(value = "INVOKE",
            target = "Ldev/latvian/mods/kubejs/script/ScriptFileInfo;preload(Ldev/latvian/mods/kubejs/script/ScriptSource;)V",
            shift = At.Shift.AFTER),cancellable = true)
    private void kubeLoader$loadFile(ScriptPack pack, ScriptFileInfo fileInfo, ScriptSource source, CallbackInfo ci) {
        String side = thiz().scriptType.name;
        Set<String> sides = ((ScriptFileInfoInterface)fileInfo).kubeLoader$getSides();

        // 如果没有定义side，则不跳过
        if (sides.isEmpty()) {
            return;
        }

        // 检查是否应该跳过加载
        boolean shouldSkip = false;
        String skipReason = "";

        // 检查是否有显式的包含规则（不以-开头的side）
        boolean hasIncludeRule = false;
        for (String s : sides) {
            if (!s.startsWith("-")) {
                hasIncludeRule = true;
                // 如果当前环境匹配任何一个不带负号的side，则不应该跳过
                if (s.equals(side)) {
                    return; // 直接返回，不跳过
                }
            }
        }

        // 如果有包含规则但没有匹配上，则应该跳过
        if (hasIncludeRule) {
            shouldSkip = true;
            skipReason = "Script type '" + side + "' not in allowed sides: " + sides;
        }

        // 检查排除规则（以-开头的side）
        if (!shouldSkip) {
            for (String s : sides) {
                if (s.startsWith("-")) {
                    String excludedSide = s.substring(1); // 去掉负号
                    if (excludedSide.equals(side)) {
                        shouldSkip = true;
                        skipReason = "Script type '" + side + "' is excluded by side: " + s;
                        break;
                    }
                }
            }
        }

        // 如果应该跳过，则取消加载
        if (shouldSkip) {
            this.thiz().scriptType.console.info("Skipped " + fileInfo.location + ": " + skipReason);
            ci.cancel();
        }
    }

    @Inject(method = "loadFromDirectory", at = @At("HEAD"))
    private void injectContentPacks(CallbackInfo ci) {
        if (thiz().scriptType.isStartup() || UtilsJS.staticServer != null) {
            MixinManager.getMixinMap().clear();
            MixinManager.loadMixins(Kubeloader.MixinPath,"");
        }
    }


    @Inject(method = "loadFromDirectory", at = @At(value = "INVOKE",
            target = "Ljava/util/List;sort(Ljava/util/Comparator;)V"),
            locals = LocalCapture.CAPTURE_FAILHARD,
            require = 1)
    private void loadCommonScripts(CallbackInfo ci, ScriptPack pack) {
        CommonScriptsLoader.loadCommonScripts(thiz(),pack,thiz().scriptType.path.getParent(),"common_scripts");

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

    public Map<String,Context> getKubeLoader$scriptContexts() {
        return this.kubeLoader$scriptContexts;
    }


    public Map<String,Object> getKubeLoader$bindings() {
        return this.kubeLoader$bindings;
    }

    public ScriptManager thiz() {
        return (ScriptManager) (Object) this;
    }
}