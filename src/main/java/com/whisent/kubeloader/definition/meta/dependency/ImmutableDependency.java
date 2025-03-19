package com.whisent.kubeloader.definition.meta.dependency;

import com.mojang.serialization.Codec;
import com.whisent.kubeloader.cpconfig.CodecUtil;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.Optional;

/**
 * @author ZZZank
 */
public record ImmutableDependency(
    DependencyType type,
    String id,
    Optional<VersionRange> versionRange,
    Optional<String> reason,
    Optional<LoadOrdering> ordering
) implements PackDependency {
    static final Codec<VersionRange> VERSION_RANGE_CODEC = Codec.STRING.comapFlatMap(
        CodecUtil.wrapUnsafeFn(VersionRange::createFromVersionSpec),
        VersionRange::toString
    );
}
