package com.whisent.kubeloader.event;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPackUtils;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import com.whisent.kubeloader.impl.mod.ModContentPackProvider;
import com.whisent.kubeloader.utils.Debugger;
import com.whisent.kubeloader.utils.modgen.ContentPackGenerator;
import com.whisent.kubeloader.utils.modgen.ContentPackModInfo;
import com.whisent.kubeloader.utils.modgen.PackModGenerator;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.core.ReloadableServerResourcesKJS;
import dev.latvian.mods.kubejs.net.KubeJSNet;
import dev.latvian.mods.kubejs.net.ReloadStartupScriptsPayload;
import dev.latvian.mods.kubejs.server.ServerScriptManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = Kubeloader.MODID)
public class KubeLoaderServerEventHandler {
    private static final Map<String, PackMetaData> packMetaDataMap = new ConcurrentHashMap<>();
    private static final Map<String, ContentPackModInfo> contentPackModInfoMap = new ConcurrentHashMap<>();
    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        Kubeloader.LOGGER.info("Registering commands for KubeLoader");
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> klCmd = Commands.literal("kl");
        klCmd.then(Commands.literal("mod")
                .then(Commands.argument("modInfo", StringArgumentType.string())
                        .suggests((context, builder) -> suggestMapKeys(contentPackModInfoMap.keySet(), builder))
                        .executes(ctx -> {
                            String modInfo = ctx.getArgument("modInfo", String.class);
                            if (!contentPackModInfoMap.containsKey(modInfo)) {
                                ctx.getSource().sendFailure(Component.literal("Unknown mod info: " + modInfo));
                                return 0;
                            }
                            try {
                                PackModGenerator.generateMod(contentPackModInfoMap.get(modInfo),
                                                ctx.getSource().getPlayer());
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            return 1;
                        })
                )
        );
        klCmd.then(Commands.literal("pack")
                .then(Commands.argument("metadata", StringArgumentType.string())
                        .suggests((context, builder) -> suggestMapKeys(packMetaDataMap.keySet(), builder))
                        .executes(ctx -> {
                            String metadata = ctx.getArgument("metadata", String.class);
                            if (!packMetaDataMap.containsKey(metadata)) {
                                ctx.getSource().sendFailure(Component.literal("Unknown pack metadata: " + metadata));
                                return 0;
                            }
                            try {
                                ContentPackGenerator.generateContentPack(packMetaDataMap.get(metadata),
                                        ctx.getSource().getPlayer());
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            return 1;
                        })
                )
        );
        klCmd.then(Commands.literal("probe")
                .then(Commands.literal("dump")
                .executes(ctx -> {
                    try {
                        ModList.get()
                                .getMods()
                                .forEach(ContentPackUtils::copyProbes);
                        ctx.getSource().sendSuccess(() -> Component.literal("Successfully dumped probe files"), false);
                    } catch (Exception e) {
                        System.out.println("Error while dumping probe files"+ e);
                        ctx.getSource().sendFailure(Component.literal("Error occurred while dumping probe files: " + e.getMessage()));
                    }
                    return 0;
                })));
        klCmd.then(Commands.literal("reload")
                .then(Commands.literal("all").executes(ctx -> {
                    ((ReloadableServerResourcesKJS) ctx.getSource().getServer().getServerResources().managers()).kjs$getServerScriptManager().reload();
                    ctx.getSource().sendSystemMessage(Component.literal("Reloaded server scripts"));
                    KubeJS.getStartupScriptManager().reload();
                    ctx.getSource().sendSystemMessage(Component.literal("Reloaded startup scripts"));
                    KubeJSNet.sendToAllPlayers(new ReloadStartupScriptsPayload(ctx.getSource().getServer().isDedicatedServer()));
                    ctx.getSource().sendSystemMessage(Component.literal("Sent startup reload to clients"));
                    ctx.getSource().sendSystemMessage(Component.literal("Done!"));
                    return 0;
                }))
                );
        dispatcher.register(klCmd);


    }
    @SubscribeEvent
    public static void onProbeCommand(CommandEvent event) {

    }
    private static CompletableFuture<Suggestions> suggestMapKeys(java.util.Set<String> keys, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(keys, builder);
    }

    private static Map<String, PackMetaData> getPackMetaDataMap() {
        return packMetaDataMap;
    }

    private static Map<String, ContentPackModInfo> getContentPackModInfoMap() {
        return contentPackModInfoMap;
    }

    public static void putMetaData(String id, PackMetaData metaData) {
        packMetaDataMap.put(id, metaData);
    }
    public static void putContentPackModInfo(String id, ContentPackModInfo modInfo) {
        contentPackModInfoMap.put(id, modInfo);
    }
    public static void init() {
        packMetaDataMap.clear();
        contentPackModInfoMap.clear();
    }

}
