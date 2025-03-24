package com.whisent.kubeloader.utils.topo;

import java.util.*;

/**
 * <a href="https://github.com/ZZZank/TopoSort/blob/main/src/main/java/zank/lib/script_topo_sort/topo/TopoSort.java">Source</a>
 *
 * @author ZZZank
 */
public final class TopoSort {

    @SuppressWarnings({"unchecked", "unused"})
    public static <T extends TopoSortable<T>> List<T> sort(Collection<T> input)
        throws TopoNotSolved, TopoPreconditionFailed {
        return sort(input instanceof List<?> l ? (List<T>) l : new ArrayList<>(input));
    }

    private static <T extends TopoSortable<T>> HashMap<T, Integer> indexSortables(Collection<T> input)
        throws TopoPreconditionFailed {
        var toIndexes = new HashMap<T, Integer>();
        var i = 0;
        for (var sortable : input) {
            var old = toIndexes.put(sortable, i++);
            if (old != null) {
                throw new TopoPreconditionFailed("values in index %s and %s are same values", i, old);
            }
        }
        return toIndexes;
    }

    public static <T extends TopoSortable<T>> List<T> sort(List<T> input)
        throws TopoNotSolved, TopoPreconditionFailed {
        //construct object->index map, sorting will only use index for better generalization
        var indexes = indexSortables(input);

        //indexing dependencies
        var requiredBy = new TreeMap<Integer, Set<Integer>>();
        var requires = new TreeMap<Integer, Set<Integer>>();
        for (var e : indexes.entrySet()) {
            var sortable = e.getKey();
            var index = e.getValue();

            var dependencyIndexes = new TreeSet<Integer>();
            for (T dependency : sortable.getTopoDependencies()) {
                var depIndex = indexes.get(dependency);
                if (depIndex == null) {
                    throw new TopoPreconditionFailed("%s (dependency of %s) not in input", dependency, sortable);
                } else if (depIndex.equals(index)) {
                    throw new TopoPreconditionFailed("%s claimed itself as its dependency", sortable);
                }
                dependencyIndexes.add(depIndex);
                requiredBy.computeIfAbsent(depIndex, (k) -> new TreeSet<>()).add(index);
            }

            requires.put(index, dependencyIndexes);
        }

        var avaliables = new ArrayList<Integer>();
        for (var e : indexes.entrySet()) {
            var dependencyCount = e.getKey().getTopoDependencies().size();
            var index = e.getValue();
            if (dependencyCount == 0) {
                avaliables.add(index);
            }
        }

        //sort
        var sorted = new ArrayList<T>();
        while (!avaliables.isEmpty()) {
            var newlyFree = new ArrayList<Integer>();

            for (var free : avaliables) {
                sorted.add(input.get(free));
                var dependents = requiredBy.getOrDefault(free, Collections.emptySet());
                for (var dependent : dependents) {
                    var require = requires.get(dependent);
                    require.remove(free);
                    if (require.isEmpty()) {
                        newlyFree.add(dependent);
                    }
                }
            }

            avaliables = newlyFree;
        }
        validateResult(requires, input);
        return sorted;
    }

    private static <T extends TopoSortable<T>> void validateResult(
        Map<Integer, Set<Integer>> requires,
        List<T> input
    ) throws TopoNotSolved {
        for (var require : requires.values()) {
            if (!require.isEmpty()) {
                var unsolved = requires.entrySet()
                    .stream()
                    .filter(e -> !e.getValue().isEmpty())
                    .toList();
                throw new TopoNotSolved(unsolved, input);
            }
        }
    }

    public static <T extends TopoSortable<T>> List<T> sortDense(List<T> input)
        throws TopoNotSolved, TopoPreconditionFailed {
        var size = input.size();
        // construct object->index map, sorting will only use index for better generalization
        var indexed = indexSortables(input);

        var dependencies = new boolean[size][size];
        var dependencyCounts = new int[size];

        for (var e : indexed.entrySet()) {
            var sortable = e.getKey();
            var index = e.getValue();
            for (var dependency : sortable.getTopoDependencies()) {
                var depIndex = indexed.get(dependency);
                if (depIndex == null) {
                    throw new TopoPreconditionFailed("%s (dependency of %s) not in input", dependency, sortable);
                } else if (depIndex.equals(index)) {
                    throw new TopoPreconditionFailed("%s claimed itself as its dependency", sortable);
                }
                dependencies[index][depIndex] = true;
            }
            dependencyCounts[index] = sortable.getTopoDependencies().size();
        }

        var avaliables = new ArrayList<Integer>();
        for (int i = 0; i < size; i++) {
            if (dependencyCounts[i] == 0) {
                avaliables.add(i);
            }
        }

        var sorted = new ArrayList<T>();
        while (!avaliables.isEmpty()) {
            var newlyFree = new ArrayList<Integer>();

            for (var free : avaliables) {
                sorted.add(input.get(free));
                for (int i = 0; i < size; i++) {
                    if (dependencies[i][free]) {
                        dependencies[i][free] = false;
                        var newCount = --dependencyCounts[i];
                        if (newCount == 0) {
                            newlyFree.add(i);
                        }
                    }
                }
            }

            avaliables = newlyFree;
        }

        for (int dependencyCount : dependencyCounts) {
            if (dependencyCount != 0) {
                throw denseNotSolved(dependencies, input);
            }
        }
        return sorted;
    }

    private static TopoNotSolved denseNotSolved(
        boolean[][] dependencies,
        List<? extends TopoSortable<?>> input
    ) {
        var notSolved = new HashMap<Integer, Set<Integer>>();
        var size = dependencies.length;
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (dependencies[row][col]) {
                    notSolved.computeIfAbsent(row, k -> new TreeSet<>())
                        .add(col);
                }
            }
        }
        return new TopoNotSolved(notSolved.entrySet(), input);
    }
}