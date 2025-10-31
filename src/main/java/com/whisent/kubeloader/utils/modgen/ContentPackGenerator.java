package com.whisent.kubeloader.utils.modgen;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import com.whisent.kubeloader.definition.meta.PackMetaDataBuilder;
import com.whisent.kubeloader.definition.meta.dependency.DependencyType;
import com.whisent.kubeloader.definition.meta.dependency.PackDependency;
import com.whisent.kubeloader.definition.meta.dependency.PackDependencyBuilder;
import com.whisent.kubeloader.utils.Debugger;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class ContentPackGenerator {
    public static PackMetaDataBuilder createMetaData(String packId) {
        return PackMetaDataBuilder.create(packId);
    }
    public static PackDependencyBuilder createDependency(DependencyType type, String id) {
        return PackDependencyBuilder.create(type, id);
    }
    @Info("You should use /kl pack <packid> command to generate it, rather than calling this method directly.")
    public static void generateContentPack(PackMetaData metaData) throws IOException {
        //System.out.print("尝试生成ContentPack");
        String packId = metaData.id();
        Path packDir = Kubeloader.PackPath.resolve(packId);
        Files.createDirectories(packDir);
        List<String> dirs =List.of("server_scripts",
                "client_scripts",
                "startup_scripts",
                "common_scripts",
                "mixin_scripts",
                "assets", "data",
                "probe");
        dirs.forEach(dir -> {
            try {
                Files.createDirectories(packDir.resolve(dir));
                //System.out.print("已生成目录：" + dir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        JsonElement jsonElement = PackMetaData.CODEC
                .encodeStart(JsonOps.INSTANCE, metaData)
                .resultOrPartial(s -> {
                    throw new IllegalStateException("Failed to serialize PackMetaData: " + s);
                }).orElseThrow(() -> new RuntimeException("Failed to serialize PackMetaData"));
        String jsonString = Kubeloader.GSON
                .newBuilder()
                .setPrettyPrinting()
                .create()
                .toJson(jsonElement) + "\n";
        Files.writeString(
                packDir.resolve(Kubeloader.META_DATA_FILE_NAME),
                jsonString,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }
    @Info("You should use /kl pack <packid> command to generate it, rather than calling this method directly.")
    public static void generateContentPack(PackMetaData metaData, ServerPlayer player) throws IOException {
        String packId = metaData.id();
        Path packDir = Kubeloader.PackPath.resolve(packId);

        // ✅ 发送开始消息（国际化）
        sendPlayerMessage(player, Component.translatable("chat.kubeloader.contentpack.start", packId)
                .withStyle(ChatFormatting.GOLD));

        try {
            // 创建主目录
            Files.createDirectories(packDir);
            sendPlayerMessage(player, Component.translatable("chat.kubeloader.contentpack.root_created")
                    .withStyle(ChatFormatting.GREEN));

            // 要创建的子目录
            List<String> dirs =
                    List.of("server_scripts", "client_scripts", "startup_scripts",
                            "mixin_scripts","common_scripts","probe", "assets", "data");

            for (String dir : dirs) {
                try {
                    Files.createDirectories(packDir.resolve(dir));
                    sendPlayerMessage(player, Component.translatable("chat.kubeloader.contentpack.dir_created", dir)
                            .withStyle(ChatFormatting.AQUA));
                } catch (IOException e) {
                    Component errorMsg = Component.translatable("chat.kubeloader.contentpack.dir_failed", dir)
                            .withStyle(ChatFormatting.RED);
                    sendPlayerMessage(player, errorMsg);
                    throw new IOException("Failed to create directory: " + packDir.resolve(dir), e);
                }
            }

            // 序列化 PackMetaData 到 JSON
            sendPlayerMessage(player,
                    Component.translatable("chat.kubeloader.contentpack.serializing")
                    .withStyle(ChatFormatting.YELLOW));

            JsonElement jsonElement = PackMetaData.CODEC
                    .encodeStart(JsonOps.INSTANCE, metaData)
                    .resultOrPartial(s -> {
                        throw new IllegalStateException("Failed to serialize PackMetaData: " + s);
                    }).orElseThrow(() -> new RuntimeException("Failed to serialize PackMetaData"));

            String jsonString = Kubeloader.GSON.newBuilder()
                    .setPrettyPrinting()
                    .create()
                    .toJson(jsonElement) + "\n";

            // 写入 metadata.json 文件
            Path metaFile = packDir.resolve(Kubeloader.META_DATA_FILE_NAME);
            Files.writeString(
                    metaFile,
                    jsonString,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

            sendPlayerMessage(player, Component.translatable("chat.kubeloader.contentpack.meta_written", Kubeloader.META_DATA_FILE_NAME)
                    .withStyle(ChatFormatting.GREEN));

            // ✅ 成功提示
            Component successMsg = Component.translatable("chat.kubeloader.contentpack.success", packId)
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
            sendPlayerMessage(player, successMsg);

            // ✅ 服务端日志
            Kubeloader.LOGGER.info("内容包生成成功: {}", packId);
            Debugger.out("内容包生成完成！ID: " + packId);
            Debugger.out("目录位置: " + packDir.toAbsolutePath());

        } catch (Exception e) {
            // ❌ 出错时通知玩家（国际化）
            Component errorMsg = Component.translatable("chat.kubeloader.contentpack.failed", e.getMessage())
                    .withStyle(ChatFormatting.RED);
            sendPlayerMessage(player, errorMsg);

            Kubeloader.LOGGER.error("生成内容包失败 (用户: {}, packId: {})", player.getName().getString(), packId, e);

            // 向上抛出异常
            if (e instanceof IOException) throw (IOException) e;
            else throw new IOException("Failed to generate content pack: " + packId, e);
        }
    }
    private static void sendPlayerMessage(ServerPlayer player, Component message) {
        player.sendSystemMessage(message);
    }
    public static void fastGenerateContentPack(String packId,List<String> authors) throws IOException {
        PackDependency dependency = PackDependencyBuilder
                .create(DependencyType.REQUIRED, "kubejs")
                .build();
        PackMetaData metaData = PackMetaDataBuilder.create(packId)
                .withAuthors(authors)
                .withVersion("1.0.0")
                .withDescription("This is a default content pack generated by KubeLoader")
                .addDependency(dependency)
                .build();
        generateContentPack(metaData);
    }
    public static void fastGenerateContentPack(String packId,List<String> authors, ServerPlayer player) throws IOException {
        PackDependency dependency;
        PackMetaData metaData;
        try {
            dependency = PackDependencyBuilder
                    .create(DependencyType.REQUIRED, "kubejs")
                    .build();
            // 假设你有方法从 ID 构建 PackMetaData
            metaData = PackMetaDataBuilder.create(packId)
                    .withAuthors(authors)
                    .withVersion("1.0.0")
                    .withDescription("This is a default content pack generated by KubeLoader")
                    .addDependency(dependency)
                    .build();
        } catch (Exception e) {
            Component error = Component.literal("Failed to create PackMetaData : " + e.getMessage())
                    .withStyle(ChatFormatting.RED);
            sendPlayerMessage(player, error);
            throw new IOException("Failed to create PackMetaData for: " + packId, e);
        }

        generateContentPack(metaData, player);
    }
}
