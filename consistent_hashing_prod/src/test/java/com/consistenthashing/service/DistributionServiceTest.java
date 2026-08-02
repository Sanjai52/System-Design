package com.consistenthashing.service;

import com.consistenthashing.consistenthash.ConsistentHashRing;
import com.consistenthashing.consistenthash.HashFunction;
import com.consistenthashing.consistenthash.StorageNode;
import com.consistenthashing.model.Student;
import com.consistenthashing.storage.InMemoryStudentStore;
import com.consistenthashing.storage.StudentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DistributionServiceTest {

    private ConsistentHashRing ring;
    private StudentStore store;
    private DistributionService service;

    private final StorageNode n1 = new StorageNode("mongo1", 27017);
    private final StorageNode n2 = new StorageNode("mongo2", 27018);
    private final StorageNode n3 = new StorageNode("mongo3", 27019);

    @BeforeEach
    void setUp() {
        ring = new ConsistentHashRing(new HashFunction(), 150);
        ring.addNode(n1);
        ring.addNode(n2);
        ring.addNode(n3);
        store = new InMemoryStudentStore();
        service = new DistributionService(ring, store);
    }

    private void seed(int count) {
        for (int i = 0; i < count; i++) {
            long roll = 1001L + i;
            Student s = new Student(roll, "S" + roll, "CS", 3);
            store.insertOn(s, ring.getNode(s.getRollNo().toString()));
        }
    }

    @Test
    void testCountsAndTotal() {
        seed(42);
        Map<String, Long> counts = service.counts();

        long sum = 0;
        for (Long c : counts.values()) sum += c;
        assertEquals(42, sum);
        assertEquals(3, counts.size());
        // each node count non-negative
        for (Long c : counts.values()) assertTrue(c >= 0);
    }

    @Test
    void testReportIncludesVnodesAndDelta() {
        seed(30);
        Map<String, Long> before = service.counts();

        var report = service.report(before);

        assertEquals(30, report.total());
        assertEquals(3, report.vnodes().size());
        // delta per node = after - before = 0 here (unchanged topology)
        for (String k : report.delta().keySet()) {
            assertEquals(0L, report.delta().get(k));
        }
    }

    @Test
    void testEmptyStoreHasZeroTotal() {
        // no students inserted -> total 0, every node count 0
        var report = service.report(Map.of());
        assertEquals(0, report.total());
        for (Long c : report.counts().values()) {
            assertEquals(0L, c);
        }
    }
}
