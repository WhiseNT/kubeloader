package com.whisent.kubeloader.files;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.whisent.kubeloader.Kubeloader;


import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
//ToDo
public class PackInfo {

    public static void main(Path path) throws IOException {
        // 创建FileConfig实例

        FileConfig config = FileConfig.builder(new File(String.valueOf(path))).build();
        config.load();
        Kubeloader.LOGGER.info("TOML文件配置:");
        String packName = config.get("pack.packName");
        String packVersion = config.get("pack.packVersion");
        String packDescription = config.get("pack.packDescription");
        String gameVersion = config.get("pack.gameVersion");
        String license = config.get("pack.license");
        List<String> authors = config.get("pack.authors");


        Kubeloader.LOGGER.info(packName + " " + packVersion + " " + packDescription + " " + gameVersion + " " + license);
    }
}
