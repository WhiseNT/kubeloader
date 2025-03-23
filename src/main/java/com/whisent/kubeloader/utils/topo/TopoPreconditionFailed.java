package com.whisent.kubeloader.utils.topo;

/**
 * <a href="https://github.com/ZZZank/TopoSort/blob/main/src/main/java/zank/lib/script_topo_sort/topo/TopoPreconditionFailed.java">Source</a>
 *
 * @author ZZZank
 */
public class TopoPreconditionFailed extends RuntimeException {

    public TopoPreconditionFailed(String message) {
        super(message);
    }

    public TopoPreconditionFailed(String format, Object... args) {
        super(String.format(format, args));
    }
}