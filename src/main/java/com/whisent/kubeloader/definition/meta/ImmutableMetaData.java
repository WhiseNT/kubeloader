package com.whisent.kubeloader.definition.meta;

import com.mojang.serialization.Codec;
import com.whisent.kubeloader.definition.meta.dependency.PackDependency;
import org.apache.maven.artifact.versioning.ComparableVersion;

import java.util.*;

/**
 * @author ZZZank
 */
record ImmutableMetaData(
    String id,
    Optional<String> name,
    Optional<String> description,
    Optional<ComparableVersion> version,
    List<String> authors,
    List<PackDependency> dependencies
) implements PackMetaData {
    static final Codec<ComparableVersion> VERSION_CODEC = Codec.STRING.xmap(ComparableVersion::new, ComparableVersion::toString);
}
