package com.whisent.kubeloader.files.topo;

import com.whisent.kubeloader.definition.ContentPack;

import java.util.*;

public class ContentPackSorter {
    LinkedHashMap<String, ContentPack> contentPacks;
    String originId;
    HashMap<String,List<String>> adjList;
    HashMap<String,PackNode> packNodes;
    ArrayList<String> sortedPacks;
    public ContentPackSorter(LinkedHashMap<String, ContentPack> contentPacks) {
        this.contentPacks = contentPacks;
        this.originId = "kubejs";
        adjList = new HashMap<String,List<String>>();
        packNodes = new HashMap<String,PackNode>();
        sortedPacks = new ArrayList<String>();
        init();
    }
    private void init() {
        for (ContentPack pack : contentPacks.values()) {

            adjList.putIfAbsent(pack.getNamespace(), new ArrayList<>());
            adjList.putIfAbsent(getRelative(pack), new ArrayList<>());

            packNodes.putIfAbsent(pack.getNamespace(), new PackNode(pack.getNamespace()));
            packNodes.putIfAbsent(getRelative(pack), new PackNode(getRelative(pack)));
        }
    }
    public void buildDependencies() {
        for (ContentPack pack : contentPacks.values()) {
            PackNode current = packNodes.get(pack.getNamespace());
            PackNode target = packNodes.get(getRelative(pack));

            if (getBeforeFlag(pack)) {
                adjList.get(pack.getNamespace()).add(getRelative(pack));
                target.inDegree++;

            } else {
                adjList.get(getRelative(pack)).add(pack.getNamespace());
                current.inDegree++;
            }
        }
    }
    public List<String> getSortedPacks() {
        Queue<PackNode> queue = new LinkedList<>();

        for (PackNode node : packNodes.values()) {
            if (node.inDegree == 0) {
                queue.add(node);
            }
        }
        while (!queue.isEmpty()) {
            PackNode node = queue.poll();
            sortedPacks.add(node.namespace);

            for (String neighbor : adjList.getOrDefault(node.namespace,Collections.emptyList())) {
                PackNode neighborNode = packNodes.get(neighbor);
                neighborNode.inDegree--;
                if (neighborNode.inDegree == 0) {
                    queue.add(neighborNode);
                }
            }
        }
        if (sortedPacks.size() != packNodes.size()) {
            throw new RuntimeException("Packs not sorted properly");
        }
        return sortedPacks;
    }

    private String getRelative (ContentPack pack) {
        return pack.getConfig().get("target").toString();
    }
    private Boolean getBeforeFlag (ContentPack pack) {
        return pack.getConfig().get("isBefore").toString().equals("true");
    }
}
