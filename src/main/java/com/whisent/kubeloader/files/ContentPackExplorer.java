package com.whisent.kubeloader.files;

import com.whisent.kubeloader.Kubeloader;
import dev.latvian.mods.kubejs.KubeJS;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class ContentPackExplorer {
    private static final String TARGET_DIR = "contentpack";
    // 主扫描方法
    public static void scanAllMods(String type) {
        ModList.get().getMods().forEach(mod -> {
            try {
                Path jarPath = getModJarPath(mod);
                if (jarPath != null) {
                    processJarFile(jarPath, mod.getModId(),type);
                }
            } catch (Exception e) {
                System.err.println("Error processing mod: " + mod.getModId());
                e.printStackTrace();
            }
        });
    }

    // 获取模组JAR物理路径
    private static Path getModJarPath(IModInfo mod) {
        return mod.getOwningFile()
                .getFile()
                .getFilePath();
    }
    // 处理单个JAR文件
    private static void processJarFile(Path jarPath, String modId,String type) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                //Kubeloader.LOGGER.info(String.valueOf(entry));
                if (isContentPackFile(entry)) {
                    handleContentPackEntry(jar, entry, modId,type);
                    Kubeloader.LOGGER.info("找到jar内文件");
                    Kubeloader.LOGGER.info(entry.getName());
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to open JAR: " + jarPath);
        }
    }
    // 判断是否为目标文件
    private static boolean isContentPackFile(JarEntry entry) {
        return entry.getName().startsWith(TARGET_DIR + "/") &&
                !entry.isDirectory() &&
                entry.getName().length() > TARGET_DIR.length() + 1;
    }
    // 处理找到的内容包文件
    private static void handleContentPackEntry(JarFile jar, JarEntry entry, String modId,String type) {
        try (InputStream is = jar.getInputStream(entry)) {
            // 解析原始路径结构（示例：contentpack/client_scripts/example.js）
            String[] pathArray = entry.getName().split("/");
            String reconstructedPath = Arrays.stream(pathArray)
                    .skip(2) // 跳过前两个元素
                    .collect(Collectors.joining("/"));
            Kubeloader.LOGGER.debug("组织元素");
            Kubeloader.LOGGER.debug(reconstructedPath);
            if (Objects.equals(pathArray[1], type)) {

                String fullPath = type + "/" + "contentpack_scripts" + "/"  + modId + "/" + reconstructedPath;
                //contentpack/server_scripts/advanced/test.js
                Kubeloader.LOGGER.debug("entry的name"+entry.getName());
                //server_scripts/contentpack_scripts/testcontentpackmod/advanced/test.js
                Kubeloader.LOGGER.debug("fullPath的name"+fullPath);
                Path path = Paths.get(fullPath);
                // 解析脚本类型和目标路径
                Path targetPath = Minecraft.getInstance().gameDirectory.toPath().resolve("kubejs").resolve(fullPath);
                Kubeloader.LOGGER.debug("目标的name"+targetPath);

                // 创建父目录
                if (Files.notExists(targetPath.getParent())) {
                    Files.createDirectories(targetPath.getParent());
                }

                // 执行文件复制
                Files.copy(is, targetPath , StandardCopyOption.REPLACE_EXISTING);
            }


        } catch (IOException e) {
            KubeJS.LOGGER.error("合并失败 [{}]: {}", modId, entry.getName(), e);
        }
    }
}
