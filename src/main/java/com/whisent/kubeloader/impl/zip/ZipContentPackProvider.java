package com.whisent.kubeloader.impl.zip;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.ContentPackProvider;
import com.whisent.kubeloader.impl.mod.ModContentPack;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipContentPackProvider  implements ContentPackProvider {
    private final File file;
    private final ZipFile zipFile;

    public ZipContentPackProvider(File file) throws IOException {
        this.file = file;
        this.zipFile = new ZipFile(file);
    }

    @Override
    public boolean isDynamic() {
        return true;
    }

    @Override
    public @Nullable ContentPack providePack() {
        return scanSingle(zipFile);
    }

    private ContentPack scanSingle(ZipFile zipFile) {
        try {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry != null && !entry.isDirectory()) {
                    return new ZipContentPack(file);
                }
            }
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
