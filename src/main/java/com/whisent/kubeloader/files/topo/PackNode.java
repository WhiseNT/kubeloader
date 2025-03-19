package com.whisent.kubeloader.files.topo;

public class PackNode {
    String namespace;
    int inDegree;
    public PackNode(String namespace) {
        this.namespace = namespace;
        this.inDegree = 0;

    }
}
