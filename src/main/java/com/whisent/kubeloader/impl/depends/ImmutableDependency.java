package com.whisent.kubeloader.impl.depends;

import com.mojang.serialization.Codec;
import com.whisent.kubeloader.definition.meta.dependency.DependencySource;
import com.whisent.kubeloader.definition.meta.dependency.DependencyType;
import com.whisent.kubeloader.definition.meta.dependency.LoadOrdering;
import com.whisent.kubeloader.definition.meta.dependency.PackDependency;
import com.whisent.kubeloader.utils.CodecUtil;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.Optional;

/**
 * @author ZZZank
 */
public record ImmutableDependency(
    DependencyType type,
    DependencySource source,
    String id,
    Optional<VersionRange> versionRange,
    Optional<String> reason,
    Optional<LoadOrdering> ordering
) implements PackDependency {
    public static final Codec<VersionRange> VERSION_RANGE_CODEC = Codec.STRING.comapFlatMap(
        CodecUtil.wrapUnsafeFn(VersionRange::createFromVersionSpec),
        VersionRange::toString
    );
}
