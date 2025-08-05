package com.whisent.kubeloader.definition.meta.dependency;

import com.whisent.kubeloader.impl.depends.ImmutableDependency;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.Objects;
import java.util.Optional;

public class PackDependencyBuilder {
    private DependencyType type;
    private DependencySource source = DependencySource.PACK;
    private String id;
    private VersionRange versionRange;
    private String reason;
    private LoadOrdering ordering;

    public static PackDependencyBuilder create(DependencyType type, String id) {
        return new PackDependencyBuilder(type, id);
    }

    private PackDependencyBuilder(DependencyType type, String id) {
        this.type = Objects.requireNonNull(type);
        this.id = Objects.requireNonNull(id);
    }

    public PackDependencyBuilder withSource(DependencySource source) {
        this.source = Objects.requireNonNull(source);
        return this;
    }

    public PackDependencyBuilder withVersionRange(VersionRange versionRange) {
        this.versionRange = versionRange;
        return this;
    }

    public PackDependencyBuilder withVersionRange(String versionSpec) throws InvalidVersionSpecificationException {
        this.versionRange = VersionRange.createFromVersionSpec(versionSpec);
        return this;
    }

    public PackDependencyBuilder withReason(String reason) {
        this.reason = reason;
        return this;
    }

    public PackDependencyBuilder withOrdering(LoadOrdering ordering) {
        this.ordering = ordering;
        return this;
    }

    public PackDependency build() {
        return new ImmutableDependency(
                type,
                source,
                id,
                Optional.ofNullable(versionRange),
                Optional.ofNullable(reason),
                Optional.ofNullable(ordering)
        );
    }
}