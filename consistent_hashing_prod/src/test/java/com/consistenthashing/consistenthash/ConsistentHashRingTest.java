package com.consistenthashing.consistenthash;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConsistentHashRingTest {

    private static final HashFunction HF = new HashFunction();
    private static final List<StorageNode> INITIAL = List.of(
            new StorageNode("mongo1", 27017),
            new StorageNode("mongo2", 27018),
            new StorageNode("mongo3", 27019)
    );

    private List<String> keys(int from, int to) {
        List<String> keys = new ArrayList<>();
        for (long r = from; r <= to; r++) keys.add(String.valueOf(r));
        return keys;
    }

    private Map<String, StorageNode> owners(ConsistentHashRing ring, List<String> keys) {
        Map<String, StorageNode> map = new HashMap<>();
        for (String k : keys) map.put(k, ring.getNode(k));
        return map;
    }

    @Test
    void testDeterministicLookup() {
        ConsistentHashRing ring = new ConsistentHashRing(HF, 150);
        INITIAL.forEach(ring::addNode);

        Map<String, StorageNode> first = owners(ring, keys(1001, 1100));
        Map<String, StorageNode> second = owners(ring, keys(1001, 1100));

        for (String k : first.keySet()) {
            assertEquals(first.get(k).key(), second.get(k).key(), "owner must be stable for key " + k);
        }
        assertNotNull(ring.getNode("any-key"));
    }

    @Test
    void testAddNodeMovesOnlyAffectedKeys() {
        ConsistentHashRing ring = new ConsistentHashRing(HF, 150);
        INITIAL.forEach(ring::addNode);

        List<String> keys = keys(1001, 2000);
        Map<String, StorageNode> before = owners(ring, keys);

        ring.addNode(new StorageNode("mongo4", 27020));
        Map<String, StorageNode> after = owners(ring, keys);

        int moved = 0;
        for (String k : keys) {
            if (!before.get(k).key().equals(after.get(k).key())) moved++;
        }
        double ratio = (double) moved / keys.size();
        // Adding 1 node to 3 should move ~1/4 of keys; allow generous slack.
        assertTrue(ratio < 0.40, "moved ratio " + ratio + " should be < 0.40");
        assertTrue(moved > 0, "some keys should migrate to the new node");
    }

    @Test
    void testRemoveNodeRedistributesKeysToRemaining() {
        ConsistentHashRing ring = new ConsistentHashRing(HF, 150);
        INITIAL.forEach(ring::addNode);

        List<String> keys = keys(1001, 2000);
        StorageNode removed = INITIAL.get(1);
        Map<String, StorageNode> before = owners(ring, keys);

        ring.removeNode(removed);
        Map<String, StorageNode> after = owners(ring, keys);

        for (String k : keys) {
            StorageNode owner = after.get(k);
            assertNotEquals(removed.key(), owner.key(), "removed node must not own any key");
            assertNotNull(owner);
        }
        // Keys previously on the removed node must now be reassigned.
        int reassigned = 0;
        for (String k : keys) {
            if (before.get(k).key().equals(removed.key())) reassigned++;
        }
        assertTrue(reassigned > 0, "removed node held keys that must be reassigned");
    }

    @Test
    void testKeyDistributionEvenAcrossNodes() {
        ConsistentHashRing ring = new ConsistentHashRing(HF, 150);
        INITIAL.forEach(ring::addNode);

        List<String> keys = keys(1001, 10000);
        Map<String, StorageNode> owners = owners(ring, keys);
        Map<String, Integer> counts = new HashMap<>();
        for (StorageNode n : INITIAL) counts.put(n.key(), 0);
        for (StorageNode owner : owners.values()) {
            counts.merge(owner.key(), 1, Integer::sum);
        }

        int per = keys.size() / INITIAL.size();
        double spread = 0.25; // allow 25% deviation from the ideal mean
        for (StorageNode n : INITIAL) {
            int c = counts.get(n.key());
            assertTrue(Math.abs(c - per) <= per * spread,
                    n.key() + " has " + c + " (expected ~" + per + " ±" + (int)(per*spread) + ")");
        }
    }

    @Test
    void testAddRemoveAreInverseOnOwnership() {
        ConsistentHashRing ring = new ConsistentHashRing(HF, 150);
        INITIAL.forEach(ring::addNode);
        StorageNode extra = new StorageNode("mongo4", 27020);

        List<String> keys = keys(1001, 5000);
        Map<String, StorageNode> beforeAdd = owners(ring, keys);

        ring.addNode(extra);
        Map<String, StorageNode> whileAdded = owners(ring, keys);
        ring.removeNode(extra);
        Map<String, StorageNode> afterRemove = owners(ring, keys);

        // After removing the extra node, ownership must match the pre-add state for all keys.
        for (String k : keys) {
            assertEquals(beforeAdd.get(k).key(), afterRemove.get(k).key(),
                    "ownership must revert after remove for key " + k);
        }
        // While added, extra node must hold some keys.
        int heldByExtra = 0;
        for (StorageNode o : whileAdded.values()) if (o.key().equals(extra.key())) heldByExtra++;
        assertTrue(heldByExtra > 0, "extra node held no keys while present");
    }
}
