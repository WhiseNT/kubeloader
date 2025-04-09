package com.whisent.kubeloader.definition.inject;

import com.whisent.kubeloader.impl.depends.SortableContentPack;

import java.util.Map;

/**
 * @author ZZZank
 */
public interface SortablePacksHolder {

    Map<String, SortableContentPack> kubeLoader$sortablePacks();
}
