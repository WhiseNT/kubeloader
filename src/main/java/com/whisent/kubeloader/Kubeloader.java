package com.whisent.kubeloader;

import com.mojang.logging.LogUtils;
import com.whisent.kubeloader.files.*;
import com.whisent.kubeloader.impl.ContentPackProviders;

import com.whisent.kubeloader.impl.mod.ModContentPackProvider;
import com.whisent.kubeloader.impl.path.PathContentPackProvider;
import com.whisent.kubeloader.impl.zip.ZipContentPackProvider;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.KubeJSPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Mod(Kubeloader.MODID)
public class Kubeloader {
    public static final String MODID = "kubeloader";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String FOLDER_NAME = "contentpacks";
    public static Path ConfigPath = KubeJSPaths.CONFIG.resolve(FOLDER_NAME);
    public static Path ResourcePath = KubeJSPaths.DIRECTORY.resolve("pack_resources");
    public static Path PackPath = KubeJSPaths.DIRECTORY.resolve(FOLDER_NAME);

    public Kubeloader() throws IOException {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();


        LOGGER.info(ResourcePath.toString());
        LOGGER.info(PackPath.toString());
        //将resource写入,先清理资源文件再进行写入
        CleanPacks();
        if (Files.notExists(ResourcePath)){
            Files.createDirectories(ResourcePath);
        }
        if (Files.notExists(PackPath)){
            Files.createDirectories(PackPath);
        }
        if (Files.notExists(ConfigPath)){
            Files.createDirectories(ConfigPath);
        }
        Minecraft.getInstance().getResourceManager().listPacks().forEach(pack -> {
            Kubeloader.LOGGER.debug("搜索到资源包："+pack.toString());
        });
        modEventBus.addListener(this::ModLoding);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::injectPacks);

        //mod
        //registerModContentPackProviders();
        //zip
        registerZipContentPackProviders();
        //path
        ContentPackProviders.register(new PathContentPackProvider(PackPath));




    }

    private static void registerModContentPackProviders() {
        // mod
        var providers = ModList.get()
            .getMods()
            .stream()
            .map(ModContentPackProvider::new)
            .toList();
        ContentPackProviders.register(providers);
    }

    private static void registerZipContentPackProviders() throws IOException {
        List<ZipContentPackProvider> list = new ArrayList<>();
        for (String s : FileIO.listZips(PackPath)) {
            Kubeloader.LOGGER.debug("找到压缩包："+s);
            File file = new File(s);
            ZipContentPackProvider zipContentPackProvider = new ZipContentPackProvider(file);
            list.add(zipContentPackProvider);
        }
        ContentPackProviders.register(list);
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
                event.addRepositorySource(new ResourcePackProvider(ResourcePath, PackType.CLIENT_RESOURCES));
            }
            case SERVER_DATA -> {
                event.addRepositorySource(new ResourcePackProvider(ResourcePath,PackType.SERVER_DATA));
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

    private void CleanPacks() {
        LOGGER.info("清理不存在的ContentPack");
        Path MinecraftDir = Minecraft.getInstance().gameDirectory.toPath();
        Path assetsDir = MinecraftDir.resolve("kubejs").resolve("pack_resources").resolve("assets");
        Path dataDir = MinecraftDir.resolve("kubejs").resolve("pack_resources").resolve("data");
        List<Path> pathCollection = new ArrayList<>();
        pathCollection.addAll(List.of(assetsDir, dataDir));
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
