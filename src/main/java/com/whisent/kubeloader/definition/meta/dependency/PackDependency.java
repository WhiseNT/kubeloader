package com.whisent.kubeloader.definition.meta.dependency;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.impl.depends.ImmutableDependency;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.Optional;

/**
 * @author ZZZank
 */
public interface PackDependency {

    DependencyType type();

    DependencySource source();

    String id();

    Optional<VersionRange> versionRange();

    Optional<String> reason();

    Optional<LoadOrdering> ordering();

    default MutableComponent toReport(ContentPack parent) {
        return Component.translatable(
            "%s declared %s %s dependency with id '%s' and version range '%s'%s",
            Component.literal(parent.toString()).withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE),
            Component.literal(this.type().toString()),
            Component.literal(this.source().toString()),
            Component.literal(this.id()).withStyle(ChatFormatting.YELLOW),
            Component.literal(this.versionRange().map(VersionRange::toString).orElse("*"))
                .withStyle(ChatFormatting.YELLOW),
            Component.literal(reason().map(", for reason '%s'"::formatted).orElse(""))
        );
    }

    Codec<PackDependency> CODEC = RecordCodecBuilder.create(
        builder -> builder.group(
            DependencyType.CODEC.fieldOf("type").forGetter(PackDependency::type),
            DependencySource.CODEC.optionalFieldOf("source", DependencySource.PACK).forGetter(PackDependency::source),
            Codec.STRING.fieldOf("id").forGetter(PackDependency::id),
            ImmutableDependency.VERSION_RANGE_CODEC.optionalFieldOf("versionRange")
                .forGetter(PackDependency::versionRange),
            Codec.STRING.optionalFieldOf("reason").forGetter(PackDependency::reason),
            LoadOrdering.CODEC.optionalFieldOf("ordering").forGetter(PackDependency::ordering)
        ).apply(builder, ImmutableDependency::new)
    );
}
