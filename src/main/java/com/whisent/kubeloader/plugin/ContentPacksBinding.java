package com.whisent.kubeloader.plugin;

import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.inject.SortablePacksHolder;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import com.whisent.kubeloader.impl.depends.SortableContentPack;
import dev.latvian.mods.kubejs.script.ScriptType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author ZZZank
 */
public class ContentPacksBinding {
    private final ScriptType type;
    private final SortablePacksHolder packsHolder;
    private final Map<String, Object> globals;

    public ContentPacksBinding(ScriptType type, SortablePacksHolder packsHolder) {
        this.type = type;
        this.packsHolder = packsHolder;
        this.globals = new HashMap<>();
    }

    public ScriptType type() {
        return type;
    }

    public PackMetaData getMetadata(String id) {
        var sortableContentPack = packsHolder.kubeLoader$sortablePacks().get(id);
        return Optional.ofNullable(sortableContentPack)
            .map(SortableContentPack::pack)
            .map(ContentPack::getMetaData)
            .orElse(null);
    }

    public void putGlobalData(String id, Object o) {
        globals.put(id, o);
    }

    public Object getGlobalData(String id) {
        return globals.get(id);
    }

    public Object getGlobalDataOrEmpty(String id) {
        return globals.getOrDefault(id, Map.of());
    }
}
