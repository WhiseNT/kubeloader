package com.whisent.kubeloader.files;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileIO {


    public static void copyAndReplaceAllFiles(Path sourceDir, Path targetDir) throws IOException {
        if (!Files.exists(sourceDir)) {
            throw new NoSuchFileException("Source directory does not exist: " + sourceDir);
        }
        if (!Files.isDirectory(sourceDir)) {
            throw new NotDirectoryException("Source is not a directory: " + sourceDir);
        }

        // 2. 删除目标目录内容（如果存在）
        if (Files.exists(targetDir)) {
            deleteDirectoryRecursively(targetDir,targetDir.getParent());
        }

        // 3. 创建目标目录
        Files.createDirectories(targetDir);

        // 4. 遍历并复制
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            stream
                    .filter(Files::isRegularFile) // 只处理文件
                    .forEach(source -> {
                        try {
                            // 计算相对路径，防止路径穿越
                            Path relativePath = sourceDir.relativize(source);
                            Path target = targetDir.resolve(relativePath);

                            // 确保目标父目录存在
                            Files.createDirectories(target.getParent());

                            // 复制并覆盖
                            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

                        } catch (IOException e) {
                            throw new UncheckedIOException("Failed to copy: " + source + " -> " + targetDir, e);
                        } catch (Exception e) {
                            throw new RuntimeException("Unexpected error during copy: " + source, e);
                        }
                    });
        } catch (RuntimeException e) {
            // 重新抛出被包装的异常
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else {
                throw new IOException("Error during file copy", e);
            }
        }

        System.out.println("✅ All files copied and replaced successfully from " + sourceDir + " to " + targetDir);
    }

    public static void copy(Path source, Path target) throws IOException {
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public static List<String> listZips(Path path) {
        try {
            if (!Files.isDirectory(path)) {
                throw new SecurityException("Access denied: Path is not a directory: " + path);
            }
            var a = Files.list(path)
                    .filter(e->e.getFileName().toString().endsWith(".zip"))
                    .map(Path::toString)
                    .collect(Collectors.toList());
            return a;
        } catch (RuntimeException | IOException e) {
            throw new RuntimeException(e);
        }
    }
    private static void deleteDirectoryRecursively(Path dir, Path basePath) throws IOException {
        if (Files.exists(dir)) {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!file.startsWith(basePath)) {
                        Kubeloader.LOGGER.warn("Attempt to delete file outside of allowed base path: " + file);
                        return FileVisitResult.CONTINUE;
                    }
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc == null) {
                        if (!dir.startsWith(basePath)) {
                            Kubeloader.LOGGER.warn("Attempt to delete directory outside of allowed base path: " + dir);
                            return FileVisitResult.CONTINUE;
                        }
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    } else {
                        throw exc;
                    }
                }
            });
        }
    }



    public static void extractAssetCopyFromZip(Path zipFilePath, String subDir) throws IOException {
        Path tempDir = Files.createTempDirectory("zip_extract");
        try {
            unzipFile(zipFilePath, tempDir);
            Path metaDir = tempDir.resolve(Kubeloader.META_DATA_FILE_NAME);
            Path namespaceDir = tempDir;
            Path sourceSubDir = namespaceDir.resolve(subDir);
            if (!Files.exists(sourceSubDir)) {
                return;
            }
            Path targetDir = Minecraft.getInstance().gameDirectory.toPath().resolve("kubejs").resolve("pack_resources")
                    .resolve(subDir).resolve(namespaceDir.getFileName()).resolve(subDir);

            copyAndReplaceAllFiles(sourceSubDir, targetDir);
        } finally {
            deleteDirectoryRecursively(tempDir,tempDir.getParent());
        }
    }
    public static PackMetaData loadMetaData(Path base) {
        try (var reader = Files.newBufferedReader(base.resolve(Kubeloader.META_DATA_FILE_NAME))) {
            var result = PackMetaData.CODEC.parse(
                    JsonOps.INSTANCE,
                    Kubeloader.GSON.fromJson(reader, JsonObject.class)
            );
            if (result.result().isPresent()) {
                return result.result().get();
            }
            var errorMessage = result.error().orElseThrow().message();
            throw new RuntimeException("Error when parsing metadata: " + errorMessage);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * 解压ZIP文件到指定目录
     *
     * @param zipFilePath ZIP文件路径
     * @param destDir 目标目录路径
     * @throws IOException 如果发生I/O错误
     */
    public static void unzipFile(Path zipFilePath, Path destDir) throws IOException {
        if (!Files.isReadable(zipFilePath)) {
            throw new IOException("无法读取ZIP文件: " + zipFilePath.toAbsolutePath());
        }
        //Kubeloader.LOGGER.info("正在解压zip文件");
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath.toFile()))) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                Path filePath = destDir.resolve(zipEntry.getName());
                if (zipEntry.isDirectory()) {
                    Files.createDirectories(filePath);
                } else {
                    Files.createDirectories(filePath.getParent());
                    Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private static Path validateNamespace(Path tempDir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir)) {
            Path namespaceDir = null;
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    if (namespaceDir != null) {
                        return null; // 根目录包含多个文件夹
                    }
                    namespaceDir = entry;
                } else {
                    throw new IOException("ZIP文件的根目录包含非目录条目: " + entry.getFileName());
                }
            }
            return namespaceDir;
        }
    }
    public static void createMcMetaFile(String filePath) throws IOException {
        Path parentDir = Paths.get(filePath).getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        try (FileWriter writer = new FileWriter(filePath.toString())) {
            writer.write(FIXED_JSON_CONTENT);
        } catch (IOException e) {
            throw e;
        }
    }
    // mcmeta文件
    private static final String FIXED_JSON_CONTENT = "{\n" +
            "  \"pack\": {\n" +
            "    \"pack_format\": 15,\n" +
            "    \"description\": \"KubeLoader Resource Pack\"\n" +
            "  }\n" +
            "}\n";
    public static void deleteAllContents(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("路径不能为空");
        }

        if (!Files.exists(path)) {
            throw new IOException("路径不存在: " + path.toAbsolutePath());
        }
        if (!Files.isDirectory(path)) {
            throw new IOException("路径不是一个目录: " + path.toAbsolutePath());
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path entry : stream) {
                deleteRecursively(entry);
            }
        } catch (IOException e) {
            throw new IOException("无法读取目录内容: " + path.toAbsolutePath(), e);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path entry : stream) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.delete(path);
    }

    public static BufferedReader stream2reader(InputStream stream) {
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
    public static BufferedReader file2reader(File file) throws FileNotFoundException {
        return new BufferedReader(new FileReader(file));
    }
}
