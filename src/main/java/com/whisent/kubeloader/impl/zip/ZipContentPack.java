package com.whisent.kubeloader.impl.zip;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPackUtils;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import com.whisent.kubeloader.impl.ContentPackBase;
import com.whisent.kubeloader.klm.MixinManager;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptSource;
import dev.latvian.mods.kubejs.util.UtilsJS;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 即将弃用,原因：应该使用ModGen生成Jar而非打包为Zip.
 */
@Deprecated
public class ZipContentPack extends ContentPackBase {
    private final Path path;
    private final String resourcePrefix;
    private final String dataPrefix;

    public ZipContentPack(Path path, PackMetaData metaData) {
        super(metaData);
        this.path = path;
        this.resourcePrefix = "pack_resources/assets/";
        this.dataPrefix = "pack_resources/data/";
    }
    
    public Path getPath() {
        return path;
    }

    @Override
    @Nullable
    protected ScriptPack createPack(PackLoadingContext context) {
        // 加载mixin脚本
        loadMixins(context);
        
        var pack = ContentPackUtils.createEmptyPack(context, id());
        //loadCommonScripts(pack, context);
        var prefix = context.folderName() + '/';
        try (var zipFile = new ZipFile(path.toFile())) {
            zipFile.stream()
                .filter(e -> !e.isDirectory())
                .filter(e -> e.getName().endsWith(".js"))
                .filter(e -> e.getName().startsWith(prefix))
                .forEach(zipEntry -> {
                    var zipFileInfo = new ScriptFileInfo(pack.info, zipEntry.getName());
                    var scriptSource = (ScriptSource) info -> {
                                var reader = new BufferedReader(new InputStreamReader(
                                        zipFile.getInputStream(zipEntry), StandardCharsets.UTF_8));
                        return reader.lines().toList();
                    };
                    context.loadFile(pack, zipFileInfo, scriptSource);
                });

            return pack;
        } catch (IOException e) {
            // TODO: log
            return null;
        }
    }
    
    /**
     * 从ZIP文件中加载mixin脚本
     * @param context 加载上下文
     */
    public void loadMixins(PackLoadingContext context) {
        if (!context.manager().scriptType.isStartup() || UtilsJS.staticServer != null) {
            return;
        }
        String mixinFolder = Kubeloader.MIXIN_FOLDER + "/";
        try (var zipFile = new ZipFile(path.toFile())) {
            zipFile.stream()
                .filter(e -> !e.isDirectory())
                .filter(e -> e.getName().endsWith(".js"))
                .filter(e -> e.getName().startsWith(mixinFolder))
                .forEach(zipEntry -> {
                    try {
                        // 读取mixin脚本内容
                        String sourceCode = new BufferedReader(new InputStreamReader(
                                zipFile.getInputStream(zipEntry), StandardCharsets.UTF_8))
                                .lines()
                                .reduce("", (a, b) -> a + "\n" + b);
                        
                        // 从注释中提取目标文件路径
                        String targetFile = extractTargetFileFromComments(sourceCode);
                        
                        // 解析mixin DSL
                        var dsls = com.whisent.kubeloader.klm.dsl.MixinDSLParser.parse(sourceCode);
                        
                        // 注册解析到的MixinDSL对象
                        String fullPath = "zip:" + path.toString() + "!/" + zipEntry.getName();
                        for (var dsl : dsls) {
                            // 如果从注释中找到了目标文件，则使用注释中的目标文件
                            // 否则使用默认的完整路径
                            if (targetFile != null && !targetFile.isEmpty()) {
                                dsl.setTargetFile(targetFile);
                            } else {
                                dsl.setTargetFile(fullPath);
                            }
                            MixinManager.addMixinDSL(targetFile, dsl);
                        }
                        
                        // 记录日志
                        if (!dsls.isEmpty()) {
                            Kubeloader.LOGGER.info("Loaded {} mixin DSLs from ZIP entry: {}", dsls.size(), fullPath);
                        }
                    } catch (IOException e) {
                        Kubeloader.LOGGER.error("加载mixin脚本时出错: {}", zipEntry.getName(), e);
                    }
                });
        } catch (IOException e) {
            Kubeloader.LOGGER.error("加载zip中的mixin脚本时出错: {}", path, e);
        }
    }
    
    /**
     * 从源代码注释中提取目标文件路径
     * 
     * @param sourceCode 源代码
     * @return 目标文件路径，如果未找到则返回null
     */
    private static String extractTargetFileFromComments(String sourceCode) {
        try (BufferedReader reader = new BufferedReader(new StringReader(sourceCode))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                if (trimmedLine.startsWith("//")) {
                    java.util.regex.Matcher matcher = MixinManager.MIXIN_COMMENT_PATTERN.matcher(trimmedLine);
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
    
    public Set<String> getNamespaces(PackType packType) {
        String prefix = packType == PackType.CLIENT_RESOURCES ? resourcePrefix : dataPrefix;
        Set<String> namespaces = new HashSet<>();

        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            zipFile.stream()
                .filter(entry -> !entry.isDirectory())
                .filter(entry -> entry.getName().startsWith(prefix))
                .map(entry -> getNamespaceFromPath(entry.getName(), prefix))
                .forEach(namespaces::add);
        } catch (IOException e) {
            // Ignore
        }

        return namespaces;
    }

    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
        String prefix = packType == PackType.CLIENT_RESOURCES ? resourcePrefix : dataPrefix;
        String path = prefix + location.getNamespace() + "/" + location.getPath();

        try (ZipFile zipFile = new ZipFile(this.path.toFile())) {
            ZipEntry entry = zipFile.getEntry(path);
            if (entry != null) {
                return () -> zipFile.getInputStream(entry);
            }
        } catch (IOException e) {
            // Ignore
        }

        return null;
    }

    private String getNamespaceFromPath(String path, String prefix) {
        String relativePath = path.substring(prefix.length());
        int firstSlash = relativePath.indexOf('/');
        if (firstSlash != -1) {
            return relativePath.substring(0, firstSlash);
        }
        return "";
    }

    @Override
    public String toString() {
        return "ZipContentPack[namespace=%s]".formatted(metaData.id());
    }
}