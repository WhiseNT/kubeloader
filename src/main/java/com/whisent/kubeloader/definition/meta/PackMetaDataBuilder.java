package com.whisent.kubeloader.definition.meta;

import com.whisent.kubeloader.definition.meta.dependency.PackDependency;
import com.whisent.kubeloader.event.KubeLoaderServerEventHandler;
import com.whisent.kubeloader.impl.depends.ImmutableMetaData;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PackMetaDataBuilder {
    private String id;
    private String name;
    private String description;
    private ArtifactVersion version;
    private final List<String> authors = new ArrayList<>();
    private final List<PackDependency> dependencies = new ArrayList<>();

    private PackMetaDataBuilder(String id) {
        this.id = Objects.requireNonNull(id);
        this.name = id;
        this.description = "";
    }

    public static PackMetaDataBuilder create(String id) {
        return new PackMetaDataBuilder(id);
    }

    public PackMetaDataBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public PackMetaDataBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public PackMetaDataBuilder withVersion(ArtifactVersion version) {
        this.version = version;
        return this;
    }

    public PackMetaDataBuilder withVersion(String version) {
        this.version = new DefaultArtifactVersion(version);
        return this;
    }

    public PackMetaDataBuilder addAuthor(String author) {
        this.authors.add(Objects.requireNonNull(author));
        return this;
    }

    public PackMetaDataBuilder addAuthors(List<String> authors) {
        this.authors.addAll(Objects.requireNonNull(authors));
        return this;
    }
    public PackMetaDataBuilder withAuthors(List<String> authors) {
        this.authors.clear();
        this.authors.addAll(Objects.requireNonNull(authors));
        return this;
    }

    public PackMetaDataBuilder addDependency(PackDependency dependency) {
        this.dependencies.add(Objects.requireNonNull(dependency));
        return this;
    }

    public PackMetaDataBuilder addDependencies(List<PackDependency> dependencies) {
        this.dependencies.addAll(Objects.requireNonNull(dependencies));
        return this;
    }

    public PackMetaData build() {
        ImmutableMetaData data = new ImmutableMetaData(
                id,
                Optional.ofNullable(name),
                Optional.ofNullable(description),
                Optional.ofNullable(version),
                List.copyOf(authors),
                List.copyOf(dependencies)
        );
        KubeLoaderServerEventHandler.putMetaData(id, data);
        return data;
    }
}
