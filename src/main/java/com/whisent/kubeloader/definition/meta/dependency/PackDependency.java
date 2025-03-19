package com.whisent.kubeloader.definition.meta.dependency;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.Optional;

/**
 * @author ZZZank
 */
public interface PackDependency {

    DependencyType type();

    String id();

    Optional<VersionRange> versionRange();

    Optional<String> reason();

    Optional<LoadOrdering> ordering();

    Codec<PackDependency> CODEC = RecordCodecBuilder.create(
        builder -> builder.group(
            DependencyType.CODEC.fieldOf("type").forGetter(PackDependency::type),
            Codec.STRING.fieldOf("id").forGetter(PackDependency::id),
            ImmutableDependency.VERSION_RANGE_CODEC.optionalFieldOf("versionRange").forGetter(PackDependency::versionRange),
            Codec.STRING.optionalFieldOf("reason").forGetter(PackDependency::reason),
            LoadOrdering.CODEC.optionalFieldOf("ordering").forGetter(PackDependency::ordering)
        ).apply(builder, ImmutableDependency::new)
    );
}
