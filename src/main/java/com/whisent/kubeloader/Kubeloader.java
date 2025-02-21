package com.whisent.kubeloader;

import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.mojang.logging.LogUtils;
import com.whisent.kubeloader.files.ContentPackExplorer;
import com.whisent.kubeloader.files.FileIO;
import com.whisent.kubeloader.files.PackInfo;
import com.whisent.kubeloader.files.ResourcePackProvider;
import cpw.mods.jarhandling.SecureJar;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.KubeJSPaths;
import dev.latvian.mods.kubejs.util.ConsoleJS;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.core.config.yaml.YamlConfiguration;
import org.apache.logging.log4j.core.config.yaml.YamlConfigurationFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Mod(Kubeloader.MODID)
public class Kubeloader {
    public static final String MODID = "kubeloader";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static Path ResourcePath = KubeJSPaths.DIRECTORY.resolve("pack_resources");
    public static Path PackPath = KubeJSPaths.DIRECTORY.resolve("contentpacks");
    public Kubeloader() throws IOException {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();


        LOGGER.info(ResourcePath.toString());
        LOGGER.info(PackPath.toString());
        if (Files.notExists(ResourcePath)){
            Files.createDirectories(ResourcePath);
        }
        if (Files.notExists(PackPath)){
            Files.createDirectories(PackPath);
        }
        CleanPacks();

        ContentPackExplorer.scanAllMods("server_scripts");
        ContentPackExplorer.scanAllMods("startup_scripts");
        ContentPackExplorer.scanAllMods("client_scripts");
        //startup会在加载脚本前被被写入
        loadScripts("client");
        loadScripts("server");

        modEventBus.addListener(this::ModLoding);
        //LoadFromMods();

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::injectPacks);

    }

    private void ModLoding(FMLClientSetupEvent event) {
        LOGGER.info("Setup启动事件");
    }

    private void injectPacks(AddPackFindersEvent event){
        //资源写入
        InjectFiles(PackPath,"assets");
        InjectFiles(PackPath,"data");
        switch (event.getPackType()) {
            case CLIENT_RESOURCES -> {
                event.addRepositorySource((RepositorySource)
                        new ResourcePackProvider(ResourcePath
                                , PackType.CLIENT_RESOURCES));
            }
            case SERVER_DATA -> {
                event.addRepositorySource((RepositorySource)
                        new ResourcePackProvider(ResourcePath
                                ,PackType.SERVER_DATA));
            }
        }
    }
    private void InjectFiles(Path PackPath,String type) {
        try (DirectoryStream<Path> namespaces = Files.newDirectoryStream(PackPath)) {
            for (Path namespaceDir : namespaces) {
                //写入文件夹类型的资源包
                if (Files.isDirectory(namespaceDir)){
                    Path assetDir = namespaceDir.resolve(type);
                    String PackName = namespaceDir.getFileName().toString();
                    if (true) {
                        //将assets全部复制
                        Path TargetDir = ResourcePath.resolve(type).resolve(PackName).resolve(type);
                        LOGGER.info("复制到位置"+TargetDir);
                        FileIO.copyAndReplaceAllFiles(assetDir, TargetDir);

                        Path packFilePath = TargetDir.getParent().resolve("pack.mcmeta");
                        Kubeloader.LOGGER.info("创建路径"+packFilePath);
                        FileIO.createMcMetaFile(packFilePath.toString());
                    }
                } else if (!Files.isDirectory(namespaceDir)) {
                    if(namespaceDir.toString().toLowerCase().endsWith(".zip")) {
                        String ZipName = namespaceDir.getFileName().toString().toLowerCase().replace(".zip", "");
                        ;
                        Path targetDir = KubeJS.getGameDirectory().resolve("kubejs")
                                .resolve("pack_resources").resolve(type).resolve(ZipName).resolve(type);
                        LOGGER.info("复制到位置"+targetDir);
                        Path packFilePath = targetDir.getParent().resolve("pack.mcmeta");
                        FileIO.extractAssetCopyFromZip(namespaceDir,type);
                        FileIO.createMcMetaFile(packFilePath.toString());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void loadScripts(String scriptType) {
        //MC根目录
        Path MinecraftDir = Minecraft.getInstance().gameDirectory.toPath();
        //非脚本**内容包根目录
        Path contentPacksDir = MinecraftDir.resolve("kubejs").resolve("contentpacks");
        if (Files.notExists(contentPacksDir)) {
            return;
        }
        KubeJS.LOGGER.info("内容包根目录" + contentPacksDir);
        try (DirectoryStream<Path> namespaces = Files.newDirectoryStream(contentPacksDir)) {
            for (Path namespaceDir : namespaces) {
                //.minecraft/kubejs/contentpacks/namespace


                String scriptTypeDir = scriptType + "_scripts";
                if (Files.isDirectory(namespaceDir)) {
                    //构建脚本类型目录路径

                    //.minecraft/kubejs/contentpacks/namespace/scripts
                    Path scriptsDir = namespaceDir.resolve(scriptTypeDir);
                    if (Files.notExists(scriptsDir)) {
                        //不存在文件夹则创建文件夹
                        Files.createDirectories(scriptsDir);
                    }
                    //搜索对应kubejs路径
                    //.minecraft/kubejs/scripts/contentpack_scripts/namespace
                    Path MainScriptPath = MinecraftDir.resolve("kubejs").resolve(scriptTypeDir);
                    Path targetDir = MainScriptPath.resolve("contentpack_scripts").resolve(namespaceDir.getFileName());
                    FileIO.copyAndReplaceAllFiles(scriptsDir,targetDir);
                }   else if (!Files.isDirectory(namespaceDir)) {
                    if(namespaceDir.toString().toLowerCase().endsWith(".zip")) {
                        String ZipName = namespaceDir.getFileName().toString().toLowerCase().replace(".zip", "");
                        Path targetDir = MinecraftDir.resolve("kubejs").resolve(scriptTypeDir)
                                .resolve("contentpack_scripts").resolve(ZipName);
                        if (Files.notExists(targetDir)) {
                            Files.createDirectories(targetDir);
                        }
                        FileIO.extractAndCopyFromZip(namespaceDir,scriptTypeDir);
                    }
                }
            }
        } catch (IOException e) {
            Kubeloader.LOGGER.error("Failed to load contentpacks!", e);
            ConsoleJS.STARTUP.error("Failed to load contentpacks!,e");
        }
    }

    private void CleanPacks() {
        LOGGER.info("清理不存在的ContentPack");
        Path MinecraftDir = Minecraft.getInstance().gameDirectory.toPath();
        Path server_scriptsDir = MinecraftDir.resolve("kubejs").resolve("server_scripts").resolve("contentpack_scripts");
        Path startup_scriptsDir = MinecraftDir.resolve("kubejs").resolve("startup_scripts").resolve("contentpack_scripts");
        Path client_scriptsDir = MinecraftDir.resolve("kubejs").resolve("client_scripts").resolve("contentpack_scripts");
        Path assetsDir = MinecraftDir.resolve("kubejs").resolve("pack_resources").resolve("assets");
        Path dataDir = MinecraftDir.resolve("kubejs").resolve("pack_resources").resolve("data");

        List<Path> pathCollection = new ArrayList<>();
        pathCollection.addAll(List.of(
                server_scriptsDir, startup_scriptsDir, client_scriptsDir,assetsDir, dataDir));
        pathCollection.forEach(path -> {
            try {
                if (Files.exists(path)) {
                    FileIO.deleteAllContents(path);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {

        }
    }

}
