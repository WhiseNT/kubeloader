package com.whisent.kubeloader.files;

import com.google.errorprone.annotations.Var;
import com.whisent.kubeloader.Kubeloader;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class ModcpLoader {
    public static void load() {
        ModList.get().getMods().stream()
                .map(IModInfo::getOwningFile)
                .forEach(c->{
                    var file = c.getFile();
                    if (file.getFileName().startsWith("contentpack")) {
                        Kubeloader.LOGGER.debug("找到file"+file.getFileName());
                    }
                    loadSingleFile((File) c.getFile());
                });
    }
    private static void loadSingleFile(File jarFile) {
        try (final var file = new JarFile(jarFile)) {
            final var entry = file.getEntry("contentpack");
            if (!entry.isDirectory()) {
                return;
            }
            var files = file.stream().filter(e -> !e.isDirectory())
                    .map(ZipEntry::getName)
                    .filter(e -> e.endsWith(".js"))
                    .filter(e -> !e.startsWith("contentpack"))
                    .map(file::getEntry)
                    .toList();
            for (var fileEntry : files) {

                try (var stream = file.getInputStream(fileEntry)) {


                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
