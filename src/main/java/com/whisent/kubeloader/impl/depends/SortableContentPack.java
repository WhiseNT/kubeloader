package com.whisent.kubeloader.impl.depends;

import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.utils.topo.TopoSortable;
import dev.latvian.mods.kubejs.script.ScriptPack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author ZZZank
 */
public class SortableContentPack implements TopoSortable<SortableContentPack> {
    private final String id;
    private final ContentPack pack;
    private final List<ScriptPack> scriptPacks;
    private final List<SortableContentPack> dependencies = new ArrayList<>();

    public SortableContentPack(String id, ContentPack pack, Collection<ScriptPack> scriptPacks) {
        this.id = id;
        this.pack = pack;
        this.scriptPacks = List.copyOf(scriptPacks);
    }

    public SortableContentPack(String id, ContentPack pack, ScriptPack scriptPack) {
        this(id, pack, List.of(scriptPack));
    }

    @Override
    public Collection<SortableContentPack> getTopoDependencies() {
        return dependencies;
    }

    @Override
    public String toString() {
        return "SortableContentPack[%s]".formatted(id);
    }

    public ContentPack pack() {
        return pack;
    }

    public String id() {
        return id;
    }

    public List<ScriptPack> scriptPacks() {
        return scriptPacks;
    }
}