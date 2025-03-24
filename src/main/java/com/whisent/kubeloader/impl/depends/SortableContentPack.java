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
    private final ScriptPack scriptPack;
    private final List<SortableContentPack> dependencies = new ArrayList<>();

    public SortableContentPack(String id, ContentPack pack, ScriptPack scriptPack) {
        this.id = id;
        this.pack = pack;
        this.scriptPack = scriptPack;
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

    public ScriptPack scriptPack() {
        return scriptPack;
    }
}
