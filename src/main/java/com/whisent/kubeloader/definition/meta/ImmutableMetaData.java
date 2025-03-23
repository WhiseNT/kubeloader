package com.whisent.kubeloader.definition.meta;

import com.mojang.serialization.Codec;
import com.whisent.kubeloader.definition.meta.dependency.PackDependency;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.*;

/**
 * @author ZZZank
 */
record ImmutableMetaData(
    String id,
    Optional<String> name,
    Optional<String> description,
    Optional<DefaultArtifactVersion> version,
    List<String> authors,
    List<PackDependency> dependencies
) implements PackMetaData {
    static final Codec<DefaultArtifactVersion> VERSION_CODEC = Codec.STRING.xmap(DefaultArtifactVersion::new, DefaultArtifactVersion::toString);
}
