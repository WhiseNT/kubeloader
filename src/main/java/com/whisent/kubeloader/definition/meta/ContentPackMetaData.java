package com.whisent.kubeloader.definition.meta;

import org.apache.maven.artifact.versioning.ComparableVersion;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author ZZZank
 */
public interface ContentPackMetaData {

    String id();

    Optional<String> name();

    Optional<String> description();

    Optional<ComparableVersion> version();

    List<String> authors();

    Set<String> depends();

    Set<String> conflicts();
}
