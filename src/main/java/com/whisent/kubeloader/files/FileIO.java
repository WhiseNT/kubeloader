package com.whisent.kubeloader.files;

import com.whisent.kubeloader.Kubeloader;
import dev.latvian.mods.kubejs.KubeJS;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileIO {


    public static void copyAndReplaceAllFiles(Path sourceDir, Path targetDir) throws IOException {
        try {

            // 删除目标目录下的所有文件和子目录
            deleteDirectoryRecursively(targetDir, KubeJS.getGameDirectory());
            // 确保目标目录存在
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            // 遍历源目录并复制所有文件
            Files.walk(sourceDir)
                    .filter(path -> {
                        try {
                            //validateAndNormalizePath(path.toString());
                            return Files.isRegularFile(path); // 只复制普通文件
                        } catch (SecurityException e) {
                            Kubeloader.LOGGER.warn("Skipping unsafe path in batch copy: " + path);
                            return false;
                        }
                    })
                    .forEach(source -> {
                        try {
                            // 计算目标文件路径
                            Path target = targetDir.resolve(sourceDir.relativize(source));
                            // 确保目标文件的父目录存在
                            Files.createDirectories(target.getParent());
                            // 复制文件，如果目标文件已存在则覆盖
                            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                        } catch (SecurityException | IOException e) {
                            Kubeloader.LOGGER.error("Error copying file: " + source, e);
                        }
                    });

            System.out.println("All files copied and replaced successfully.");
        } catch (SecurityException e) {
            Kubeloader.LOGGER.error("Security violation in batch copy operation: " + sourceDir + " -> " + targetDir, e);
            throw e;
        } catch (IOException e) {
            Kubeloader.LOGGER.error("Error in batch copy operation", e);
            throw new RuntimeException("Failed in batch copy operation", e);
        }
    }

    private static void deleteDirectoryRecursively(Path dir, Path basePath) throws IOException {
        if (Files.exists(dir)) {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // 安全检查：确保文件路径在允许的基路径范围内
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
                        // 安全检查：确保目录路径在允许的基路径范围内
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

    public static void extractAndCopyFromZip(Path zipFilePath, String subDir) throws IOException {
        Path tempDir = Files.createTempDirectory("zip_extract");
        Kubeloader.LOGGER.info("缓存路径"+tempDir.toString());
        try {
            unzipFile(zipFilePath, tempDir);
            Path namespaceDir = validateNamespace(tempDir);
            if (namespaceDir == null) {
                throw new IOException("ZIP文件的根目录未找到任何命名空间文件夹");
            }
            Path sourceSubDir = namespaceDir.resolve(subDir);
            if (!Files.exists(sourceSubDir)) {
                return;
            }
            Path targetDir = KubeJS.getGameDirectory().resolve("contentpack").resolve(subDir)
                    .resolve("contentpack_scripts").resolve(namespaceDir.getFileName());
            Kubeloader.LOGGER.info("ZIP缓存路径"+sourceSubDir);
            Kubeloader.LOGGER.info("ZIP写入路径"+targetDir);
            copyAndReplaceAllFiles(sourceSubDir, targetDir);
        } finally {
            deleteDirectoryRecursively(tempDir,tempDir.getParent());
        }
    }

    public static void extractAssetCopyFromZip(Path zipFilePath, String subDir) throws IOException {
        // 创建临时目录
        Path tempDir = Files.createTempDirectory("zip_extract");

        try {
            // 解压ZIP文件的所有内容到临时目录
            unzipFile(zipFilePath, tempDir);
            // 检查临时目录的根目录是否仅包含一个命名空间文件夹
            Path namespaceDir = validateNamespace(tempDir);
            if (namespaceDir == null) {
                throw new IOException("ZIP文件的根目录未找到任何命名空间文件夹");
            }

            // 获取指定子目录的路径
            Path sourceSubDir = namespaceDir.resolve(subDir);
            if (!Files.exists(sourceSubDir)) {
                return;
            }
            Path targetDir = KubeJS.getGameDirectory().resolve("contentpack").resolve("pack_resources")
                    .resolve(subDir).resolve(namespaceDir.getFileName()).resolve(subDir);

            // 复制指定子目录下的所有内容到目标目录
            copyAndReplaceAllFiles(sourceSubDir, targetDir);
        } finally {
            // 删除临时目录及其内容
            deleteDirectoryRecursively(tempDir,tempDir.getParent());
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
        Kubeloader.LOGGER.info("正在解压zip文件");
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

    /**
     * 验证临时目录的根目录是否仅包含一个命名空间文件夹
     *
     * @param tempDir 临时目录路径
     * @return 命名空间文件夹路径，如果没有找到则返回null
     * @throws IOException 如果发生I/O错误
     */
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
        // 确保父目录存在
        Path parentDir = Paths.get(filePath).getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        // 写入文件
        try (FileWriter writer = new FileWriter(filePath.toString())) {
            writer.write(FIXED_JSON_CONTENT);
            System.out.println("成功创建 .mcmeta 文件: " + filePath);
        } catch (IOException e) {
            System.err.println("创建 .mcmeta 文件时出错: " + e.getMessage());
            throw e;
        }
    }

    // 定义固定的 JSON 字符串
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
            throw new IOException("给定路径不是一个目录: " + path.toAbsolutePath());
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
                    deleteRecursively(entry); // 递归删除子目录中的内容
                }
            }
        }

        Files.delete(path); // 删除文件或空目录
    }
}
