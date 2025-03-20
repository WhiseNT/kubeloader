package com.whisent.kubeloader.definition.meta;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.whisent.kubeloader.definition.meta.dependency.PackDependency;
import org.apache.maven.artifact.versioning.ComparableVersion;

import java.util.List;
import java.util.Optional;

/**
 * @author ZZZank
 */
public interface PackMetaData {

    String id();

    Optional<String> name();

    Optional<String> description();

    Optional<ComparableVersion> version();

    List<String> authors();

    List<PackDependency> dependencies();

    static PackMetaData minimal(String id) {
        return new ImmutableMetaData(id, Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of());
    }

    Codec<PackMetaData> CODEC = RecordCodecBuilder.create(
        builder -> builder.group(
            Codec.STRING.fieldOf("id").forGetter(PackMetaData::id),
            Codec.STRING.optionalFieldOf("name").forGetter(PackMetaData::name),
            Codec.STRING.optionalFieldOf("description").forGetter(PackMetaData::description),
            ImmutableMetaData.VERSION_CODEC.optionalFieldOf("version").forGetter(PackMetaData::version),
            Codec.STRING.listOf().optionalFieldOf("authors", List.of()).forGetter(PackMetaData::authors),
            PackDependency.CODEC.listOf()
                .optionalFieldOf("dependencies", List.of())
                .forGetter(PackMetaData::dependencies)
        ).apply(builder, ImmutableMetaData::new)
    );
}
