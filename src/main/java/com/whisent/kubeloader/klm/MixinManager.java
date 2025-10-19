package com.whisent.kubeloader.klm;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.klm.dsl.MixinDSL;
import com.whisent.kubeloader.klm.dsl.MixinDSLParser;
import com.whisent.kubeloader.utils.Debugger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MixinManager {
    private static final Map<String, List<MixinDSL>> mixinMap = new ConcurrentHashMap<>();
    
    // 匹配 //mixin(...) 注释的正则表达式
    public static final Pattern MIXIN_COMMENT_PATTERN = Pattern.compile("//\\s*mixin\\s*\\(\\s*value\\s*=\\s*\"([^\"]+)\"\\s*\\)");

    public static Map<String, List<MixinDSL>> getMixinMap() {
        return mixinMap;
    }

    public static void addMixinDSL(String targetPath, MixinDSL dsl) {
        mixinMap.putIfAbsent(targetPath, new ArrayList<>());
        if (mixinMap.get(targetPath) != null ) {
            mixinMap.get(targetPath).add(dsl);
        }
    }

    public static void loadMixins(Path dir, String path) {
        // 检查目录是否存在
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            Kubeloader.LOGGER.debug("Mixin directory does not exist or is not a directory: {}", dir);
            return;
        }
        
        if (!path.isEmpty() && !path.endsWith("/")) {
            path = path + "/";
        }

        String pathPrefix = path;

        try {
            Iterator<Path> var8 = Files.walk(dir, 10, new FileVisitOption[]{FileVisitOption.FOLLOW_LINKS}).filter((x$0) -> {
                return Files.isRegularFile(x$0, new LinkOption[0]);
            }).toList().iterator();

            while(true) {
                String fileName;
                Path file;
                do {
                    if (!var8.hasNext()) {
                        return;
                    }

                    file = var8.next();
                    fileName = dir.relativize(file).toString().replace(File.separatorChar, '/');
                } while(!fileName.endsWith(".js") && (!fileName.endsWith(".ts") || fileName.endsWith(".d.ts")));

                // 加载MixinDSL文件
                String fullPath = pathPrefix + fileName;
                String sourceCode = Files.readString(file);
                
                // 从注释中提取目标文件路径
                String targetFile = extractTargetFileFromComments(sourceCode);
                Debugger.out("mixin路径"+fullPath);
                Debugger.out("目标文件"+targetFile);
                List<MixinDSL> dsls = MixinDSLParser.parse(sourceCode);
                
                // 注册解析到的MixinDSL对象
                for (MixinDSL dsl : dsls) {
                    // 如果从注释中找到了目标文件，则使用注释中的目标文件
                    // 否则使用默认的完整路径
                    if (targetFile != null && !targetFile.isEmpty()) {
                        dsl.setSourcePath(fullPath);
                        dsl.setTargetFile(targetFile);
                    } else {
                        dsl.setTargetFile(fullPath);
                    }
                    addMixinDSL(targetFile, dsl);
                }
                
                // 记录日志
                if (!dsls.isEmpty()) {
                    Kubeloader.LOGGER.info("Loaded {} mixin DSLs from {}", dsls.size(), fullPath);
                }
            }
        } catch (IOException var7) {
            Kubeloader.LOGGER.error("Error loading mixins from directory: {}", dir, var7);
        }
    }
    
    /**
     * 从源代码注释中提取目标文件路径
     * 
     * @param sourceCode 源代码
     * @return 目标文件路径，如果未找到则返回null
     */
    private static String extractTargetFileFromComments(String sourceCode) {
        try {
            BufferedReader reader = new BufferedReader(new StringReader(sourceCode));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                if (trimmedLine.startsWith("//")) {
                    Matcher matcher = MIXIN_COMMENT_PATTERN.matcher(trimmedLine);
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                }
            }
        } catch (IOException e) {
            Kubeloader.LOGGER.error("Error extracting target file from comments", e);
        }
        return null;
    }
}