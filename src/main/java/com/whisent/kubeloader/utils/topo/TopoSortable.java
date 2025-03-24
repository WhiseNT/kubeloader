package com.whisent.kubeloader.utils.topo;

import java.util.Collection;

/**
 * <a href="https://github.com/ZZZank/TopoSort/blob/main/src/main/java/zank/lib/script_topo_sort/topo/TopoSortable.java">Source</a>
 *
 * @author ZZZank
 */
public interface TopoSortable<T extends TopoSortable<T>> {

    Collection<T> getTopoDependencies();
}