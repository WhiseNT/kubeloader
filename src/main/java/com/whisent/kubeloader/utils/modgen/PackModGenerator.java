package com.whisent.kubeloader.utils.modgen;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import com.whisent.kubeloader.files.FileIO;
import dev.latvian.mods.kubejs.KubeJSPaths;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PackModGenerator {
    @Info("You should use /kl mod <modid> command to generate it, rather than calling this method directly.")
    public static void generateMod(ContentPackModInfo packInfo) throws IOException {
        String modId = packInfo.id;
        Path outputDir = KubeJSPaths.LOCAL.resolve(modId+"-generated");

        // 1. 创建目录结构
        createDirectoryStructure(outputDir, modId);

        // 2. 生成 mods.toml
        generateModsToml(outputDir, packInfo);
        //generateManifestMF(outputDir, packInfo);

        generateDefaultResources(outputDir, modId);
        generateDefaultScripts(outputDir, modId);
        generatePackMcmeta(outputDir, modId, 15);

        // 4. 打包为 JAR
        Path jarFile = KubeJSPaths.EXPORT.resolve(modId+"-" + packInfo.version + ".jar");
        createJar(outputDir, jarFile);
        cleanCache(outputDir);
        //System.out.println("✅ Mod 生成完成！");
        //System.out.println("📁 项目目录: " + outputDir.toAbsolutePath());
        //System.out.println("📦 JAR 文件: " + jarFile.toAbsolutePath());
    }
    @Info("You should use /kl mod <modid> command to generate it, rather than calling this method directly.")
    public static void generateMod(ContentPackModInfo packInfo, ServerPlayer player) throws IOException {
        String modId = packInfo.id;
        Path outputDir = KubeJSPaths.LOCAL.resolve(modId + "-generated");

        // ✅ 使用 translatable 发送消息
        sendPlayerMessage(player, Component.translatable("chat.kubeloader.mod.start", modId).withStyle(ChatFormatting.GOLD));

        try {
            // 1. 创建目录结构
            createDirectoryStructure(outputDir, modId);
            sendPlayerMessage(player, Component.translatable("chat.kubeloader.mod.dir_created").withStyle(ChatFormatting.GREEN));

            // 2. 生成 mods.toml
            generateModsToml(outputDir, packInfo);
            sendPlayerMessage(player, Component.translatable("chat.kubeloader.mod.mods_toml_done").withStyle(ChatFormatting.GREEN));

            // 3. 生成资源和脚本
            generateDefaultResources(outputDir, modId);
            sendPlayerMessage(player, Component.translatable("chat.kubeloader.mod.resources_copied").withStyle(ChatFormatting.GREEN));

            generateDefaultScripts(outputDir, modId);
            sendPlayerMessage(player, Component.translatable("chat.kubeloader.mod.scripts_copied").withStyle(ChatFormatting.GREEN));

            generatePackMcmeta(outputDir, modId, 15);
            sendPlayerMessage(player, Component.translatable("chat.kubeloader.mod.mcmeta_done").withStyle(ChatFormatting.GREEN));

            // 4. 打包为 JAR
            Path jarFile = KubeJSPaths.EXPORT.resolve(modId + "-" + packInfo.version + ".jar");
            createJar(outputDir, jarFile);
            sendPlayerMessage(player, Component.translatable("chat.kubeloader.mod.jar_created", jarFile.getFileName().toString())
                    .withStyle(ChatFormatting.AQUA));

            // 5. 清理缓存
            cleanCache(outputDir);
            sendPlayerMessage(player, Component.translatable("chat.kubeloader.mod.cleanup_done").withStyle(ChatFormatting.GRAY));

            // ✅ 最终成功提示
            Component successMsg = Component.translatable("chat.kubeloader.mod.success")
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
            sendPlayerMessage(player, successMsg);

            // ✅ 服务端日志
            Kubeloader.LOGGER.info("Mod generated: {} -> {}", modId, jarFile.getFileName());
            //System.out.println("Mod 生成完成！");
            //System.out.println("项目目录: " + outputDir.toAbsolutePath());
            //System.out.println("JAR 文件: " + jarFile.toAbsolutePath());

        } catch (IOException e) {
            // ❌ 失败消息也国际化
            Component errorMsg = Component.translatable("chat.kubeloader.mod.failed", e.getMessage())
                    .withStyle(ChatFormatting.RED);
            sendPlayerMessage(player, errorMsg);

            Kubeloader.LOGGER.error("Mod 生成失败 (ModId: {})", modId, e);
            throw e;
        }
    }
    private static void sendPlayerMessage(ServerPlayer player, Component message) {
        player.sendSystemMessage(message);
    }

    private static void createDirectoryStructure(Path outputDir, String modId) throws IOException {
        Files.createDirectories(outputDir.resolve("META-INF"));
        Files.createDirectories(outputDir.resolve("contentpacks"));
    }

    private static void generateModsToml(Path outputDir, ContentPackModInfo info) throws IOException {
        Path tomlPath = outputDir.resolve("META-INF/mods.toml");
        if (!Files.exists(outputDir.resolve("META-INF"))) {
            Files.createDirectories(outputDir.resolve("META-INF"));
        }
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(tomlPath))) {
            writer.println("modLoader=\"lowcodefml\"");
            writer.println("loaderVersion=\"" + info.forge_version + "\"");
            writer.println("license=\"" + info.license +"\"");
            if (info.issuePage!=null) writer.println("issueTrackerURL=\""+ info.issuePage +"\"");
            if (info.homepage!=null) writer.println("displayURL=\"" + info.homepage+ "\"");
            if (info.logoFile!=null) writer.println("  logoFile=\"" + info.logoFile + "\"");
            writer.println();

            writer.println("[[mods]]");
            writer.println("  modId=\"" + info.id + "\"");
            writer.println("  version=\"" + info.version + "\"");
            writer.println("  displayName=\"" + info.name + "\"");
            writer.println("  authors=\"" + Arrays.toString(info.authors) + "\"");
            writer.println("  description='''" + info.description + "'''");

            writer.println();
            for (ModDependency dep : info.modDependencies) {
                writer.println(dep.toTomlString(info.id));
            }
        }
    }

    private static void generateDefaultResources(Path baseDir, String modId) throws IOException {
        Path packDir = Kubeloader.PackPath.resolve(modId);
        if (Files.exists(packDir.resolve("assets"))) {
            FileIO.copyAndReplaceAllFiles(packDir.resolve("assets"),baseDir.resolve("assets"));
        }
        if (Files.exists(packDir.resolve("data"))) {
            FileIO.copyAndReplaceAllFiles(packDir.resolve("data"),baseDir.resolve("data"));
        }

    }
    private static void cleanCache(Path targetDir) throws IOException {
        FileIO.deleteAllContents(targetDir);
        Files.delete(targetDir);
    }


    private static void generateDefaultScripts(Path baseDir, String modId) throws IOException {
        Path outputDir = baseDir.resolve("contentpacks");
        Path packDir = Kubeloader.PackPath.resolve(modId);
        List<Path> pathsToCopy;
        String[] EXCLUDED_DIRS = {"assets", "data"};

        try (Stream<Path> stream = Files.walk(packDir, 1)) {
            pathsToCopy = stream
                    .filter(path -> !path.equals(packDir))
                    .filter(path -> {
                        Path relPath = packDir.relativize(path);
                        return java.util.Arrays.stream(EXCLUDED_DIRS)
                                .noneMatch(exclude -> relPath.equals(Paths.get(exclude)));
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            pathsToCopy = List.of();
        }
        for (Path source : pathsToCopy) {
            try {
                Path relPath = packDir.relativize(source);
                Path target = outputDir.resolve(relPath);
                if (Files.isDirectory(source)) {
                    FileIO.copyAndReplaceAllFiles(source, target);
                } else {
                    Files.createDirectories(target.getParent());
                    FileIO.copy(source, target);
                }
            } catch (IOException e) {
                throw new IOException("Failed to copy: " + source + " -> " + outputDir.resolve(packDir.relativize(source)), e);
            }
        }
    }
    /**
     * 生成 pack.mcmeta 文件
     *
     * @param outputDir 生成的目标目录（通常是资源根目录）
     * @param packFormat  pack格式版本（Minecraft版本相关）
     * @throws IOException 如果写入失败
     */
    public static void generatePackMcmeta(Path outputDir, String modid, int packFormat) throws IOException {
        // 确保输出目录存在
        Files.createDirectories(outputDir);

        // 构建 pack.mcmeta 文件路径
        Path mcmetaPath = outputDir.resolve("pack.mcmeta");

        // 定义 JSON 内容
        String jsonContent = String.format(
                "{\n" +
                        "  \"pack\": {\n" +
                        "    \"description\": \"%s\",\n" +
                        "    \"pack_format\": %d\n" +
                        "  }\n" +
                        "}",
                modid+" resources", packFormat
        );

        // 写入文件，UTF-8 编码
        Files.write(
                mcmetaPath,
                jsonContent.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,           // 如果文件不存在则创建
                StandardOpenOption.TRUNCATE_EXISTING // 如果已存在则覆盖
        );

        //System.out.println("✅ pack.mcmeta 生成成功: " + mcmetaPath.toAbsolutePath());
    }


    private static void createJar(Path sourceDir, Path jarFile) throws IOException {
        // ✅ 使用 try-with-resources 包裹 Files.walk，确保 Stream 被关闭
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFile));
             Stream<Path> stream = Files.walk(sourceDir)) {

            stream.filter(path -> !path.equals(sourceDir))
                  .forEach(path -> {
                try {
                    // 计算相对路径，使用 / 分隔
                    String entryName = sourceDir.relativize(path)
                            .toString()
                            .replace("\\", "/");

                    // 如果是目录，补上 /
                    if (Files.isDirectory(path)) {
                        entryName = entryName + "/";
                    }

                    // 创建 JarEntry
                    JarEntry entry = new JarEntry(entryName);
                    entry.setTime(Files.getLastModifiedTime(path).toMillis());
                    jos.putNextEntry(entry);

                    // 如果是文件，写入内容
                    if (Files.isRegularFile(path)) {
                        Files.copy(path, jos);
                    }

                    jos.closeEntry();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to add " + path, e);
                }
            });

        } // ✅ jos 和 stream 都在这里自动关闭
    }
    public static void fastGenerateMod(String packId) throws IOException {
        ContentPackModInfo info = ContentPackModInfo.createPackInfo(packId).fromMetaData(packId).build();
        generateMod(info);
    }
    public static ContentPackModInfo.Builder createModInfo(String packId) {
        return new ContentPackModInfo.Builder();
    }
    public static ContentPackModInfo.Builder createModInfo(String packId,PackMetaData metaData) {
        return new ContentPackModInfo.Builder().fromMetaData(metaData);
    }
    public static ModDependency.Builder createModDependency(String modId) {
        return ModDependency.create(modId);
    }


}
