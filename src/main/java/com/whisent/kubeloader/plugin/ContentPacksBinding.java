package com.whisent.kubeloader.plugin;

import com.google.common.collect.Maps;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.inject.SortablePacksHolder;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import com.whisent.kubeloader.impl.depends.SortableContentPack;
import dev.latvian.mods.kubejs.script.ScriptType;

import java.util.*;

/**
 * @author ZZZank
 */
public class ContentPacksBinding {
    public static final Map<ScriptType, Map<String, Object>> TYPED_GLOBALS = new EnumMap<>(ScriptType.class);

    private final ScriptType type;
    private final Map<String, SortableContentPack> packs;
    private final Map<String, Object> globals;

    public ContentPacksBinding(ScriptType type, SortablePacksHolder packsHolder) {
        this.type = type;
        this.packs = packsHolder.kubeLoader$sortablePacks();
        this.globals = TYPED_GLOBALS.computeIfAbsent(type, t -> new HashMap<>());
    }

    public ScriptType type() {
        return type;
    }

    public boolean isLoaded(String id) {
        return packs.containsKey(id);
    }

    public PackMetaData getMetadata(String id) {
        var sortableContentPack = packs.get(id);
        return Optional.ofNullable(sortableContentPack)
            .map(SortableContentPack::pack)
            .map(ContentPack::getMetaData)
            .orElse(null);
    }

    public Map<String, PackMetaData> getAllMetadata() {
        return Collections.unmodifiableMap(Maps.transformValues(packs, s -> s.pack().getMetaData()));
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

    public Map<String, Object> getAllGlobalData() {
        return Collections.unmodifiableMap(globals);
    }
}
