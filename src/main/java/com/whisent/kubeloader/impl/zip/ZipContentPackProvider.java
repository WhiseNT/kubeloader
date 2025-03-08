package com.whisent.kubeloader.impl.zip;

import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.ContentPackProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
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
    public @NotNull Collection<? extends ContentPack> providePack() {
        var got = scanSingle(zipFile);
        return got == null ? List.of() : List.of(got);
    }

    @Nullable
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
