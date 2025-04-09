package com.whisent.kubeloader.files;

import com.whisent.kubeloader.Kubeloader;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileIO {


    public static void copyAndReplaceAllFiles(Path sourceDir, Path targetDir) throws IOException {
        try {

            // 删除目标目录下的所有文件和子目录
            deleteDirectoryRecursively(targetDir, Minecraft.getInstance().gameDirectory.toPath());
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
            Path namespaceDir = validateNamespace(tempDir);
            if (namespaceDir == null) {
                throw new IOException("ZIP文件的根目录未找到任何命名空间文件夹");
            }
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
}
