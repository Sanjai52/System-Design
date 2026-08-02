package com.consistenthashing.consistenthash;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.NavigableMap;
import java.util.TreeMap;

public class ConsistentHashRing {

    private final HashFunction hashFunction;
    private final int virtualNodesPerNode;

    private final NavigableMap<Long, StorageNode> ring = new TreeMap<>(Long::compareUnsigned);
    private final Set<StorageNode> physicalNodes = new HashSet<>();

    public ConsistentHashRing(HashFunction hashFunction, int virtualNodesPerNode) {
        this.hashFunction = hashFunction;
        this.virtualNodesPerNode = virtualNodesPerNode;
    }

    public void addNode(StorageNode node) {
        if (!physicalNodes.add(node)) {
            return;
        }
        for (int i = 0; i < virtualNodesPerNode; i++) {
            long h = hashFunction.hash(vnodeKey(node, i));
            ring.put(h, node);
        }
    }

    public void removeNode(StorageNode node) {
        if (!physicalNodes.remove(node)) {
            return;
        }
        for (int i = 0; i < virtualNodesPerNode; i++) {
            long h = hashFunction.hash(vnodeKey(node, i));
            ring.remove(h, node);
        }
    }

    public StorageNode getNode(String key) {
        return getNode(hashFunction.hash(key));
    }

    public StorageNode getNode(long hash) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("hash ring is empty");
        }
        var entry = ring.ceilingEntry(hash);
        return entry != null ? entry.getValue() : ring.firstEntry().getValue();
    }

    public List<StorageNode> getPhysicalNodes() {
        return physicalNodes.stream()
                .sorted(Comparator.comparing(StorageNode::key))
                .toList();
    }

    public Map<String, Integer> vnodeCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (StorageNode n : ring.values()) {
            counts.merge(n.key(), 1, Integer::sum);
        }
        return counts;
    }

    public int size() {
        return ring.size();
    }

    private static String vnodeKey(StorageNode node, int replicaIndex) {
        return node.key() + "#" + replicaIndex;
    }
}
