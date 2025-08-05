package com.whisent.kubeloader.utils.mod_gen;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.files.FileIO;
import dev.latvian.mods.kubejs.KubeJSPaths;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class PackModGenerator {
    public static void generateMod(ContentPackInfo packInfo) throws IOException {
        String modId = packInfo.id;
        Path outputDir = KubeJSPaths.LOCAL_CACHE.resolve(modId+"-generated");

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
        System.out.println("✅ Mod 生成完成！");
        System.out.println("📁 项目目录: " + outputDir.toAbsolutePath());
        System.out.println("📦 JAR 文件: " + jarFile.toAbsolutePath());
    }

    private static void createDirectoryStructure(Path outputDir, String modId) throws IOException {
        Files.createDirectories(outputDir.resolve("META-INF"));
        Files.createDirectories(outputDir.resolve("contentpacks"));
    }

    private static void generateModsToml(Path outputDir, ContentPackInfo info) throws IOException {
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
    private static void generateManifestMF(Path outputDir, ContentPackInfo info) throws IOException {
        Path manifestPath = outputDir.resolve("META-INF/MANIFEST.MF");
        Files.createDirectories(manifestPath.getParent());

        // 构建时间戳：ISO 格式，如 2025-07-11T22:09:29+0800
        String timestamp = java.time.ZonedDateTime.now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(manifestPath))) {
            // === 标准头部 ===
            writer.println("Manifest-Version: 1.0");

            // === 规范元数据（API 层）===
            writer.println("Specification-Title: " + info.id); // 通常用 modId 作为规范名
            writer.println("Specification-Vendor: " + String.join(", ", info.authors));
            writer.println("Specification-Version: " + 1); // 如 1.0.4 → 1

            // === 实现元数据（实现层）===
            writer.println("Implementation-Title: " + info.name);
            writer.println("Implementation-Version: " + info.version);
            writer.println("Implementation-Vendor: " + String.join(", ", info.authors));
            writer.println("Implementation-Timestamp: " + timestamp);

            // 必须以空行结束（JAR 规范要求）
            writer.println();
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

        System.out.println("✅ pack.mcmeta 生成成功: " + mcmetaPath.toAbsolutePath());
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
        ContentPackInfo info = new ContentPackInfo(packId);
        generateMod(info);
    }
    public static ContentPackInfo newPackInfo(String packId) {
        return new ContentPackInfo(packId);
    }


}
