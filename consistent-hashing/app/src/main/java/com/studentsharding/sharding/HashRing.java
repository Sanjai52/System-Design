package com.studentsharding.sharding;

import com.studentsharding.dto.RingPoint;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class HashRing {

    private static final double TWO_POW_256 = Math.pow(2, 256);

    private final int virtualNodes;
    private final TreeMap<BigInteger, String> ring = new TreeMap<>();
    private final Set<String> physicalNodes = new HashSet<>();

    public HashRing(int virtualNodes) {
        this.virtualNodes = virtualNodes;
    }

    public void addNode(String nodeId) {
        for (int i = 0; i < virtualNodes; i++) {
            ring.put(hash(nodeId + "#" + i), nodeId);
        }
        physicalNodes.add(nodeId);
    }

    public void removeNode(String nodeId) {
        ring.values().removeIf(nodeId::equals);
        physicalNodes.remove(nodeId);
    }

    public String findNode(BigInteger key) {
        SortedMap<BigInteger, String> tail = ring.tailMap(key, true);
        Map.Entry<BigInteger, String> entry = tail.isEmpty() ? ring.firstEntry() : tail.firstEntry();
        return entry.getValue();
    }

    public Set<String> physicalNodes() {
        return Collections.unmodifiableSet(physicalNodes);
    }

    public int virtualPointCount() {
        return ring.size();
    }

    public Map<String, Integer> virtualNodesPerNode() {
        Map<String, Integer> counts = new HashMap<>();
        ring.values().forEach(nodeId -> counts.merge(nodeId, 1, Integer::sum));
        return counts;
    }

    public List<RingPoint> points() {
        List<RingPoint> result = new ArrayList<>(ring.size());
        ring.forEach((hash, nodeId) -> result.add(new RingPoint(hash.doubleValue() / TWO_POW_256, nodeId)));
        return result;
    }

    public static BigInteger hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return new BigInteger(1, md.digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
