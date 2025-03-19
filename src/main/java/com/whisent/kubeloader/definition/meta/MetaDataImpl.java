package com.whisent.kubeloader.definition.meta;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.apache.maven.artifact.versioning.ComparableVersion;

import java.util.*;

/**
 * @author ZZZank
 */
record MetaDataImpl(
    String id,
    Optional<String> name,
    Optional<String> description,
    Optional<ComparableVersion> version,
    List<String> authors,
    Set<String> depends,
    Set<String> conflicts
) implements ContentPackMetaData {
    static final Codec<ComparableVersion> VERSION_CODEC = Codec.STRING.xmap(ComparableVersion::new, ComparableVersion::toString);
    static final Codec<Set<String>> STRING_SET_CODEC = Codec.STRING.listOf().xmap(Set::copyOf, List::copyOf);

    public static final Codec<ContentPackMetaData> CODEC = RecordCodecBuilder.create(
        builder -> builder.group(
            Codec.STRING.fieldOf("id").forGetter(ContentPackMetaData::id),
            Codec.STRING.optionalFieldOf("name").forGetter(ContentPackMetaData::name),
            Codec.STRING.optionalFieldOf("description").forGetter(ContentPackMetaData::description),
            VERSION_CODEC.optionalFieldOf("version").forGetter(ContentPackMetaData::version),
            Codec.STRING.listOf().fieldOf("authors").forGetter(ContentPackMetaData::authors),
            STRING_SET_CODEC.fieldOf("depends").forGetter(ContentPackMetaData::depends),
            STRING_SET_CODEC.fieldOf("conflicts").forGetter(ContentPackMetaData::conflicts)
        ).apply(builder, MetaDataImpl::new)
    );
}
