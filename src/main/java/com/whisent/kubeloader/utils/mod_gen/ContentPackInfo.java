package com.whisent.kubeloader.utils.mod_gen;

import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPackUtils;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import com.whisent.kubeloader.definition.meta.dependency.PackDependency;
import com.whisent.kubeloader.files.FileIO;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ContentPackInfo {
    public String id;
    public String name;
    public String description;
    public String version;
    public String[] authors;
    public String mc_version = "[1.20.1,1.21)";
    public String forge_version = "[47,)";
    public String license = "MIT";
    public String issuePage = null;
    public String homepage = null;
    public List<ModDependency> modDependencies = new ArrayList<>();
    public ContentPackInfo(String packId) {
        initFromMetaData(packId);

    }
    public ContentPackInfo initFromMetaData(String packId) {
        Path metaDataDir = Kubeloader.PackPath.resolve(packId).resolve("contentpack.json");
        PackMetaData metaData = ContentPackUtils.loadMetaDataOrThrow(metaDataDir);

        this.id = metaData.id();
        this.name = metaData.name().orElse(null);
        this.description = metaData.description().orElse(null);
        this.version = metaData.version().orElse(null).toString();
        this.authors = metaData.authors().toArray(new String[0]);
        return this;
    }
    public ContentPackInfo setId(String id) {
        this.id = id;
        return this;
    }
    public ContentPackInfo setName(String name) {
        this.name = name;
        return this;
    }
    public ContentPackInfo setDescription(String description) {
        this.description = description;
        return this;
    }
    public ContentPackInfo setAuthors(String[] authors) {
        this.authors = authors;
        return this;
    }

    public void setVersion(String version) {
        this.version = version;
    }
    public ContentPackInfo setModDependencies(List<ModDependency> modDependencies) {
        this.modDependencies = modDependencies;
        return this;
    }
    public ContentPackInfo setMcVersion(String mc_version) {
        this.mc_version = mc_version;
        return this;
    }
    public ContentPackInfo setForgeVersion(String forge_version) {
        this.forge_version = forge_version;
        return this;
    }
    public ContentPackInfo setLicense(String license) {
        this.license = license;
        return this;
    }
    public ContentPackInfo setIssuePage(String issuePage) {
        this.issuePage = issuePage;
        return this;
    }
    public ContentPackInfo setHomepage(String homepage) {
        this.homepage = homepage;
        return this;
    }
}
