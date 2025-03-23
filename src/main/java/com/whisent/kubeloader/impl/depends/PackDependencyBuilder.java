package com.whisent.kubeloader.impl.depends;

import com.whisent.kubeloader.definition.meta.dependency.DependencyType;
import com.whisent.kubeloader.definition.meta.dependency.LoadOrdering;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author ZZZank
 */
public class PackDependencyBuilder {

    public void build(Collection<SortableContentPack> sortables) {
        var indexed = sortables.stream()
            .collect(Collectors.toMap(SortableContentPack::id, Function.identity()));
        for (var sortable : sortables) {
            for (var dependency : sortable.pack().getMetaData().dependencies()) {
                if (dependency.type() != DependencyType.REQUIRED && dependency.type() != DependencyType.OPTIONAL) {
                    continue;
                }
                var target = indexed.get(dependency.id());
                switch (dependency.ordering().orElse(LoadOrdering.NONE)) {
                    case NONE -> {}
                    case AFTER -> sortable.getTopoDependencies().add(target);
                    case BEFORE -> target.getTopoDependencies().add(sortable);
                }
            }
        }
    }
}
