package com.whisent.kubeloader.files;

import com.whisent.kubeloader.Kubeloader;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptSource;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ContentScriptSources implements ScriptSource {

    @Override
    public List<String> readSource(ScriptFileInfo scriptFileInfo) throws IOException {
        return List.of();
    }
    public interface FromMod extends ScriptSource {
        jarFix getJarFix(ScriptFileInfo info) throws IOException;

        @Override
        default List<String> readSource(ScriptFileInfo info) throws IOException {
            jarFix jarFix = getJarFix(info);
            JarFile jar = jarFix.getJarFile();
            JarEntry entry = jarFix.getJarEntry();
            try (InputStream inputStream = jar.getInputStream(entry);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                var list = new ArrayList<String>();
                while ((line = reader.readLine()) != null) {
                    list.add(line);
                }
                return list;
            }
        }
    }
}
