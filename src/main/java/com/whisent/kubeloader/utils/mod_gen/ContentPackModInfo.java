package com.whisent.kubeloader.utils.mod_gen;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPackUtils;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import com.whisent.kubeloader.event.KubeLoaderServerEventHandler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ContentPackModInfo {
    public final String id;
    public final String name;
    public final String description;
    public final String version;
    public final String[] authors;
    public final String mc_version;
    public final String forge_version;
    public final String license;
    public final String issuePage;
    public final String homepage;
    public final List<ModDependency> modDependencies;

    // 私有构造，强制使用 Builder
    private ContentPackModInfo(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.version = builder.version;
        this.authors = builder.authors.clone(); // 防御性拷贝
        this.mc_version = builder.mc_version;
        this.forge_version = builder.forge_version;
        this.license = builder.license;
        this.issuePage = builder.issuePage;
        this.homepage = builder.homepage;
        this.modDependencies = new ArrayList<>(builder.modDependencies); // 不可变副本
    }


    public static ContentPackModInfo.Builder createPackInfo(String packId) {
        return new Builder();
    }

    // 未来可以加：toJson(), toString(), equals/hashCode 等

    // ========================
    // Builder 内部类
    // ========================
    public static class Builder {
        private String id;
        private String name;
        private String description;
        private String version = "1.0.0";
        private String[] authors = new String[0];
        private String mc_version = "[1.20.1,1.21)";
        private String forge_version = "[47,)";
        private String license = "MIT";
        private String issuePage;
        private String homepage;
        private List<ModDependency> modDependencies = new ArrayList<>();

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder withVersion(String version) {
            this.version = version;
            return this;
        }

        public Builder withAuthors(String... authors) {
            this.authors = authors.clone();
            return this;
        }

        public Builder withMcVersion(String mc_version) {
            this.mc_version = mc_version;
            return this;
        }

        public Builder withForgeVersion(String forge_version) {
            this.forge_version = forge_version;
            return this;
        }

        public Builder withLicense(String license) {
            this.license = license;
            return this;
        }

            public Builder withIssuePage(String issuePage) {
            this.issuePage = issuePage;
            return this;
        }

        public Builder withHomepage(String homepage) {
            this.homepage = homepage;
            return this;
        }

        public Builder withModDependencies(List<ModDependency> modDependencies) {
            this.modDependencies = new ArrayList<>(modDependencies);
            return this;
        }

        public Builder addModDependency(ModDependency dep) {
            this.modDependencies.add(dep);
            return this;
        }
        public Builder fromMetaData() {
            return fromMetaData(this.id);
        }

        public Builder fromMetaData(String packId) {
            Path metaDataDir = Kubeloader.PackPath.resolve(packId).resolve("contentpack.json");
            PackMetaData metaData = ContentPackUtils.loadMetaDataOrThrow(metaDataDir);

            return this
                    .withId(metaData.id())
                    .withName(metaData.name().orElse(null))
                    .withDescription(metaData.description().orElse(null))
                    .withVersion(metaData.version().orElse(null).toString())
                    .withAuthors(metaData.authors().toArray(String[]::new));
        }
        public Builder fromMetaData(PackMetaData metaData) {
            return this
                    .withId(metaData.id())
                    .withName(metaData.name().orElse(null))
                    .withDescription(metaData.description().orElse(null))
                    .withVersion(metaData.version().orElse(null).toString())
                    .withAuthors(metaData.authors().toArray(String[]::new));
        }

        public ContentPackModInfo build() {
            if (id == null || id.isEmpty()) {
                throw new IllegalStateException("ContentPack ID is required.");
            }
            ContentPackModInfo info = new ContentPackModInfo(this);
            KubeLoaderServerEventHandler.putContentPackModInfo(id, info);
            return info;
        }
    }
}