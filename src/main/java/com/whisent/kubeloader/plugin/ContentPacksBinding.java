package com.whisent.kubeloader.plugin;

import com.google.common.collect.Maps;
import com.whisent.kubeloader.definition.ContentPack;
import com.whisent.kubeloader.definition.inject.SortablePacksHolder;
import com.whisent.kubeloader.definition.meta.PackMetaData;
import com.whisent.kubeloader.impl.depends.SortableContentPack;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.typings.Info;

import java.util.*;

/**
 * @author ZZZank
 */
public class ContentPacksBinding {
    public static final Map<ScriptType, Map<String, Object>> TYPED_GLOBALS = new EnumMap<>(ScriptType.class);

    static {
        for (var scriptType : ScriptType.values()) {
            TYPED_GLOBALS.put(scriptType, new HashMap<>());
        }
    }

    private final ScriptType type;
    private final SortablePacksHolder packsHolder;

    public ContentPacksBinding(ScriptType type, SortablePacksHolder packsHolder) {
        this.type = type;
        // "why not get packsHolder.kubeLoader$sortablePacks() in advance", you might ask
        // Well, at this stage, packs map in script manager is not initialized yet, so we defer accessing
        this.packsHolder = packsHolder;
    }

    public ScriptType type() {
        return type;
    }

    @Info("""
        @return `true` if a ContentPack with provided `id` is present, `false` otherwise""")
    public boolean isLoaded(String id) {
        return packsHolder.kubeLoader$sortablePacks().containsKey(id);
    }

    @Info("""
        @return The metadata from ContentPack with provided `id`, or `null` if there's no such ContentPack""")
    public PackMetaData getMetadata(String id) {
        var sortableContentPack = packsHolder.kubeLoader$sortablePacks().get(id);
        return Optional.ofNullable(sortableContentPack)
            .map(SortableContentPack::pack)
            .map(ContentPack::getMetaData)
            .orElse(null);
    }

    @Info("""
        ContentPack id -> ContentPack metadata""")
    public Map<String, PackMetaData> getAllMetadata() {
        return Collections.unmodifiableMap(Maps.transformValues(
            packsHolder.kubeLoader$sortablePacks(),
            s -> s.pack().getMetaData()
        ));
    }

    @Info("""
        Put value into ContentPack shared data for **current** script type
        
        @see {@link type} Current script type
        @see {@link getAllSharedFor} View ContentPack shared data for another script type.""")
    public void putShared(String id, Object o) {
        TYPED_GLOBALS.get(type).put(id, o);
    }

    @Info("""
        Get ContentPack shared data for **current** script type
        
        @see {@link type} Current script type
        @see {@link getAlSharedFor} View ContentPack shared data for another script type.""")
    public Object getShared(String id) {
        return getShared(this.type, id);
    }

    @Info("""
        Get ContentPack shared data for specified script type
        
        @see {@link type} Current script type
        @see {@link getAlSharedFor} View ContentPack shared data for another script type.""")
    public Object getShared(ScriptType type, String id) {
        return getAllSharedFor(type).get(id);
    }

    @Info("""
        View all ContentPack shared data for **current** script type
        
        The return value is **immutable**, which means you can't put value into it
        
        @see {@link type} Current script type
        @see {@link getAllSharedFor} View ContentPack shared data for another script type.""")
    public Map<String, Object> getAllSharedForCurrent() {
        return getAllSharedFor(type);
    }

    @Info("""
        View all ContentPack shared data for specified script type.
        
        The return value is **immutable**, which means you can't put value into it
        
        @see {@link getAllSharedForCurrent} View all ContentPack shared data for **current** script type
        @see {@link putShared} Put value into ContentPack shared data for **current** script type""")
    public Map<String, Object> getAllSharedFor(ScriptType type) {
        return Collections.unmodifiableMap(TYPED_GLOBALS.get(type));
    }
}
