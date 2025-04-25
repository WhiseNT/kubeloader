package com.whisent.kubeloader.impl.depends;

import com.mojang.serialization.Codec;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import com.whisent.kubeloader.definition.meta.dependency.PackDependency;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.*;

/**
 * @author ZZZank
 */
public record ImmutableMetaData(
    String id,
    Optional<String> name,
    Optional<String> description,
    Optional<ArtifactVersion> version,
    List<String> authors,
    List<PackDependency> dependencies
) implements PackMetaData {
    public static final Codec<ArtifactVersion> VERSION_CODEC = Codec.STRING.xmap(DefaultArtifactVersion::new, ArtifactVersion::toString);
}
