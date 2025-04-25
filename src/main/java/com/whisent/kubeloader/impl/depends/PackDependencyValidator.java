package com.whisent.kubeloader.impl.depends;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.meta.dependency.DependencyType;
import com.whisent.kubeloader.definition.meta.dependency.PackDependency;
import dev.architectury.platform.Platform;
import net.minecraft.network.chat.Component;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.*;
import java.util.function.Consumer;

/**
 * @author ZZZank
 */
public class PackDependencyValidator {
    private final DupeHandling duplicationHandling;
    private Map<String, ContentPack> named;

    public PackDependencyValidator(DupeHandling duplicationHandling) {
        this.duplicationHandling = Objects.requireNonNull(duplicationHandling);
    }

    public DependencyReport validate(Collection<ContentPack> packs) {
        var report = new DependencyReport();
        this.named = indexByName(packs, report);
        if (!report.errors().isEmpty() && duplicationHandling == DupeHandling.ERROR) {
            return report;
        }
        for (var pack : packs) {
            for (var dependency : pack.getMetaData().dependencies()) {
                validateSingle(pack, dependency, report);
            }
        }
        named = null;
        return report;
    }

    protected void validateSingle(ContentPack pack, PackDependency dependency, DependencyReport report) {
        boolean targetPresent;
        ArtifactVersion targetVersion;
        switch (dependency.source()) {
            case PACK -> {
                var target = this.named.get(dependency.id());
                targetPresent = target != null;
                targetVersion = targetPresent
                    ? target.getMetaData().version().orElse(null)
                    : null;
            }
            case MOD -> {
                var target = Platform.getMod(dependency.id());
                targetPresent = target != null;
                targetVersion = targetPresent
                    ? new DefaultArtifactVersion(target.getVersion())
                    : null;
            }
            default -> throw new IllegalStateException("Unexpected dependency source: " + dependency.source());
        }

        var type = dependency.type();
        switch (type) {
            case REQUIRED, OPTIONAL, RECOMMENDED -> {
                Consumer<Component> reporter = switch (type) {
                    case REQUIRED -> report::addError;
                    case OPTIONAL -> report::addWarning;
                    case RECOMMENDED -> report::addInfo;
                    default -> throw new IllegalStateException();
                };
                if (!targetPresent) {
                    // required but not found
                    reporter.accept(dependency.toReport(pack).append(", but ContentPack with such id is not present"));
                } else if (dependency.versionRange().isPresent()) {
                    if (targetVersion == null) {
                        // specific version but no version
                        reporter.accept(dependency.toReport(pack).append(", but ContentPack with such id did not provide a version info"));
                    } else if (!dependency.versionRange().get().containsVersion(targetVersion)) {
                        // specific version but not matched
                        reporter.accept(dependency.toReport(pack).append(", but ContentPack with such id is at version '%s'".formatted(targetVersion)));
                    }
                }
            }
            case INCOMPATIBLE, DISCOURAGED -> {
                if (!targetPresent) {
                    return;
                }
                Consumer<Component> reporter = type == DependencyType.INCOMPATIBLE
                    ? report::addError
                    : report::addWarning;
                if (dependency.versionRange().isEmpty()) {
                    // any version not allowed
                    reporter.accept(dependency.toReport(pack)
                        .append(", but ContentPack with such id exists"));
                } else if (targetVersion == null) {
                    // specific version, but got none
                    reporter.accept(dependency.toReport(pack)
                        .append(", but ContentPack with such id did not provide version information"));
                } else if (dependency.versionRange().get().containsVersion(targetVersion)) {
                    // excluded version
                    reporter.accept(dependency.toReport(pack)
                        .append(", but ContentPack with such id is at version '%s'".formatted(targetVersion)));
                }
            }
        }
    }

    private Map<String, ContentPack> indexByName(
        Collection<ContentPack> packs,
        DependencyReport report
    ) {
        var named = new HashMap<String, ContentPack>();
        for (var pack : packs) {
            var namespace = pack.id();
            var old = named.get(namespace);
            if (old == null) {
                named.put(namespace, pack);
                continue;
            }
            var error = Component.translatable(
                "ContentPack %s and %s declared the same namespace '%s'",
                old,
                pack,
                namespace
            );
            switch (this.duplicationHandling) {
                case ERROR -> report.addError(error);
                case PREFER_LAST -> {
                    named.put(namespace, pack);
                    Kubeloader.LOGGER.warn(Component.empty()
                        .append(error)
                        .append(", overwriting old one")
                        .getString());
                }
                case PREFER_FIRST -> Kubeloader.LOGGER.warn(Component.empty()
                    .append(error)
                    .append(", keeping old one")
                    .getString());
            }
        }
        return named;
    }

    public enum DupeHandling {
        ERROR,
        PREFER_FIRST,
        PREFER_LAST
    }
}
