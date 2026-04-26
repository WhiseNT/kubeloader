package com.whisent.kubeloader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.whisent.kubeloader.compat.GraalJSCompat;
import com.whisent.kubeloader.event.KubeLoaderClientEventHandler;
import com.whisent.kubeloader.impl.ContentPackProviders;
import com.whisent.kubeloader.impl.dummy.DummyContentPack;
import com.whisent.kubeloader.impl.dummy.DummyContentPackProvider;
import com.whisent.kubeloader.impl.mod.ModContentPackProvider;
import com.whisent.kubeloader.impl.path.PathContentPackProvider;
import com.whisent.kubeloader.impl.path.PathContentPackRepositorySource;
import com.whisent.kubeloader.impl.zip.ZipContentPackRepositorySource;
import com.whisent.kubeloader.network.NetworkHandler;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.KubeJSPaths;
import net.minecraft.server.packs.PackType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Kubeloader.MODID)
public class Kubeloader
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "kubeloader";
    public static final String FOLDER_NAME = "contentpacks";
    public static final String MIXIN_FOLDER = "mixin_scripts";
    public static final String COMMON_SCRIPTS = "common_scripts";
    public static final String MIXIN_IDENTIFIER = "KLM";
    //public static final String COMMON_SCRIPTS = "common_scripts";
    public static final String CONFIG_FOLDER = "config";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String META_DATA_FILE_NAME = "contentpack.json";

    public static Path PackPath = KubeJSPaths.DIRECTORY.resolve(FOLDER_NAME);
    //public static Path CommonPath = KubeJSPaths.DIRECTORY.resolve(COMMON_SCRIPTS);
    public static Path ConfigPath = KubeJSPaths.DIRECTORY.resolve(CONFIG_FOLDER);
    public static Path MixinPath = KubeJSPaths.DIRECTORY.resolve(MIXIN_FOLDER);
    public static Path CommonPath = KubeJSPaths.DIRECTORY.resolve(COMMON_SCRIPTS);

    public Kubeloader(IEventBus modEventBus, ModContainer modContainer) throws IOException {
        GraalJSCompat.init();
        ConfigManager.init();



        //LOGGER.info(ResourcePath.toString());
        //LOGGER.info(PackPath.toString());
        //将resource写入,先清理资源文件再进行写入
        if (Files.notExists(PackPath)){
            Files.createDirectories(PackPath);
        }
        if (Files.notExists(ConfigPath)){
            Files.createDirectories(ConfigPath);
        }
        if (Files.notExists(MixinPath)) {
            Files.createDirectories(MixinPath);
        }
        if (Files.notExists(CommonPath)) {
            Files.createDirectories(CommonPath);
        }

        
        // Register config
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::injectPacks);



        //mod
        registerModContentPackProviders();
        ContentPackProviders.register(
            //path
            new PathContentPackProvider(PackPath),
            // zip
            //new ZipContentPackProvider(PackPath),
            //kubejs dummy, for sorting content packs
            new DummyContentPackProvider(List.of(new DummyContentPack(KubeJS.MOD_ID, null)))
        );

    }

    private static void registerModContentPackProviders() {
        var providers = ModList.get()
                .getMods()
                .stream()
                .map(ModContentPackProvider::new)
                .toList();
        ContentPackProviders.register(providers);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Setup启动事件");
    }

    private void injectPacks(AddPackFindersEvent event){
        //资源写入
        //InjectFiles(PackPath,"assets");
        //InjectFiles(PackPath,"data");
        switch (event.getPackType()) {
            case CLIENT_RESOURCES -> {
                //event.addRepositorySource(new ResourcePackProvider(ResourcePath, PackType.CLIENT_RESOURCES));
                // 添加PathContentPack资源
                event.addRepositorySource(new PathContentPackRepositorySource(PackType.CLIENT_RESOURCES));

                event.addRepositorySource(new ZipContentPackRepositorySource(PackType.CLIENT_RESOURCES));

            }
            case SERVER_DATA -> {
                //event.addRepositorySource(new ResourcePackProvider(ResourcePath, PackType.SERVER_DATA));
                // 添加PathContentPack数据
                event.addRepositorySource(new PathContentPackRepositorySource(PackType.SERVER_DATA));

                event.addRepositorySource(new ZipContentPackRepositorySource(PackType.SERVER_DATA));
            }
        }
    }
    
    @Deprecated
    /*
    private void InjectFiles(Path PackPath,String type) {
        try (DirectoryStream<Path> namespaces = Files.newDirectoryStream(PackPath)) {
            for (Path namespaceDir : namespaces) {
                //写入文件夹类型的资源包
                if (Files.isDirectory(namespaceDir)){
                    Path assetDir = namespaceDir.resolve(type);
                    String PackName = namespaceDir.getFileName().toString();
                    //将assets全部复制
                    Path TargetDir = ResourcePath.resolve(type).resolve(PackName).resolve(type);
                    FileIO.copyAndReplaceAllFiles(assetDir, TargetDir);

                    Path packFilePath = TargetDir.getParent().resolve("pack.mcmeta");
                    Kubeloader.LOGGER.info("创建路径"+packFilePath);
                    FileIO.createMcMetaFile(packFilePath.toString());
                } else if (!Files.isDirectory(namespaceDir)) {
                    if(namespaceDir.toString().toLowerCase().endsWith(".zip")) {
                        String ZipName = namespaceDir.getFileName().toString().toLowerCase().replace(".zip", "");
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
    @Deprecated
    private static void CleanPacks() {
        Path assetsDir = KubeJSPaths.DIRECTORY.resolve("pack_resources").resolve("assets");
        Path dataDir = KubeJSPaths.DIRECTORY.resolve("pack_resources").resolve("data");
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

     */

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            KubeLoaderClientEventHandler.init();
        }
    }

}