package com.whisent.kubeloader.impl.mod;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPackUtils;
import com.whisent.kubeloader.definition.PackLoadingContext;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import com.whisent.kubeloader.impl.ContentPackBase;
import com.whisent.kubeloader.mixinjs.MixinManager;
import com.whisent.kubeloader.mixinjs.dsl.MixinDSL;
import com.whisent.kubeloader.mixinjs.dsl.MixinDSLParser;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptSource;
import dev.latvian.mods.kubejs.util.UtilsJS;
import net.minecraftforge.forgespi.language.IModInfo;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Matcher;

/**
 * @author ZZZank
 */
public class ModContentPack extends ContentPackBase {
    private final IModInfo mod;

    public ModContentPack(IModInfo mod, PackMetaData metaData) {
        super(metaData);
        this.mod = mod;

    }

    @Override
    @Nullable
    protected ScriptPack createPack(PackLoadingContext context) {
        loadMixins(context);
        var pack = ContentPackUtils.createEmptyPack(context, id());
        var prefix = Kubeloader.FOLDER_NAME + '/' + context.folderName() + '/';
        try (var file = new JarFile(mod.getOwningFile().getFile().getFilePath().toFile())) {
            var parent = file.getEntry(Kubeloader.FOLDER_NAME + '/' + context.folderName());
            if (parent == null || !parent.isDirectory()) {
                return null;
            }

            file.stream()
                .filter(e -> !e.isDirectory())
                .filter(e -> e.getName().endsWith(".js"))
                .filter(e -> e.getName().startsWith(prefix))
                .forEach(jarEntry -> {
                    var fileInfo = new ScriptFileInfo(pack.info, jarEntry.getName());
                    var scriptSource = (ScriptSource) info -> {
                        var reader = new BufferedReader(new InputStreamReader(file.getInputStream(jarEntry)));
                        return reader.lines().toList();
                    };
                    context.loadFile(pack, fileInfo, scriptSource);
                });
            return pack;
        } catch (IOException e) {
            return null;
        }
    }

    public void loadMixins(PackLoadingContext  context) {
        if (context.manager().scriptType.isStartup() || UtilsJS.staticServer != null) {
            String mixinFolder = Kubeloader.MIXIN_FOLDER + "/";
            try (var file = new JarFile(mod.getOwningFile().getFile().getFilePath().toFile())) {
                file.stream()
                        .filter(e -> !e.isDirectory())
                        .filter(e -> e.getName().endsWith(".js"))
                        .filter(e -> e.getName().startsWith(mixinFolder))
                        .forEach(jarEntry -> {
                            try {
                                // 读取mixin脚本内容
                                String sourceCode = new BufferedReader(new InputStreamReader(
                                        file.getInputStream(jarEntry)))
                                        .lines()
                                        .reduce("", (a, b) -> a + "\n" + b);

                                // 从注释中提取目标文件路径
                                String targetFile = extractTargetFileFromComments(sourceCode);

                                // 解析mixin DSL
                                List<MixinDSL> dsls = MixinDSLParser.parse(sourceCode);

                                // 注册解析到的MixinDSL对象
                                String fullPath = "mod:" + mod.getModId() + "!/" + jarEntry.getName();
                                for (MixinDSL dsl : dsls) {
                                    // 如果从注释中找到了目标文件，则使用注释中的目标文件
                                    // 否则使用默认的完整路径
                                    if (targetFile != null && !targetFile.isEmpty()) {
                                        dsl.setTargetFile(targetFile);
                                    } else {
                                        dsl.setTargetFile(fullPath);
                                    }
                                    MixinManager.addMixinDSL(fullPath, dsl);
                                }

                                // 记录日志
                                if (!dsls.isEmpty()) {
                                    Kubeloader.LOGGER.info("Loaded {} mixin DSLs from mod JAR entry: {}", dsls.size(), fullPath);
                                }
                            } catch (IOException e) {
                                Kubeloader.LOGGER.error("Failed to read mixin file from mod JAR: {}", jarEntry.getName(), e);
                            }
                        });
            } catch (IOException e) {
                Kubeloader.LOGGER.error("Failed to load mixins from mod: {}", mod.getModId(), e);
            }
        }

    }

    /**
     * 从源代码注释中提取目标文件路径
     *
     * @param sourceCode 源代码
     * @return 目标文件路径，如果未找到则返回null
     */
    private String extractTargetFileFromComments(String sourceCode) {
        try (BufferedReader reader = new BufferedReader(new StringReader(sourceCode))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                if (trimmedLine.startsWith("//")) {
                    Matcher matcher = MixinManager.MIXIN_COMMENT_PATTERN.matcher(trimmedLine);
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

    @Override
    public String toString() {
        return "ModContentPack[mod=%s]".formatted(mod.getModId());
    }
}
