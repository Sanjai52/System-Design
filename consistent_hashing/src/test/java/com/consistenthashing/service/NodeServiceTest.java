package com.consistenthashing.service;

import com.consistenthashing.consistenthash.ConsistentHashRing;
import com.consistenthashing.consistenthash.HashFunction;
import com.consistenthashing.consistenthash.StorageNode;
import com.consistenthashing.model.Student;
import com.consistenthashing.storage.InMemoryStudentStore;
import com.consistenthashing.storage.StudentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NodeServiceTest {

    private ConsistentHashRing ring;
    private StudentStore store;
    private NodeService service;

    private final StorageNode n1 = new StorageNode("mongo1", 27017);
    private final StorageNode n2 = new StorageNode("mongo2", 27018);
    private final StorageNode n3 = new StorageNode("mongo3", 27019);
    private final StorageNode n4 = new StorageNode("mongo4", 27020);

    @BeforeEach
    void setUp() {
        ring = new ConsistentHashRing(new HashFunction(), 150);
        ring.addNode(n1);
        ring.addNode(n2);
        ring.addNode(n3);
        store = new InMemoryStudentStore();
        service = new NodeService(ring, store);
    }

    private void seed(int count) {
        for (int i = 0; i < count; i++) {
            long roll = 1001L + i;
            Student s = new Student(roll, "Student" + roll, "CS", 3);
            StorageNode owner = ring.getNode(s.getRollNo().toString());
            store.insertOn(s, owner);
        }
    }

    private int total() {
        int total = 0;
        for (StorageNode n : ring.getPhysicalNodes()) total += store.allStudentsOn(n).size();
        return total;
    }

    @Test
    void testListNodesInitiallyThree() {
        assertEquals(3, service.listNodes().size());
    }

    @Test
    void testAddNodeMigratesOnlyAffected() {
        seed(60);
        int beforeTotal = total();

        Map<String, Long> before = counts();
        NodeService.NodeChange change = service.addNode(n4.host(), n4.port());

        // total preserved
        assertEquals(beforeTotal, total());
        // migrated is a strict subset (adding 1/4 of nodes moves ~1/4 of keys)
        assertTrue(change.migrated() > 0);
        assertTrue(change.migrated() < beforeTotal);
        // new node holds some records
        assertTrue(change.after().getOrDefault(n4.key(), 0L) > 0,
                "new node should receive migrated records");
        // every student now lives on its ring owner
        for (Student s : allStudentsNow()) {
            StorageNode owner = ring.getNode(s.getRollNo().toString());
            assertNotNull(store.findByRollNoOn(s.getRollNo(), owner));
        }
    }

    @Test
    void testRemoveNodeRedistributesToRemaining() {
        seed(60);
        // move some onto mongo4 first so removal has real work
        service.addNode(n4.host(), n4.port());
        int beforeTotal = total();

        NodeService.NodeChange change = service.removeNode(n4.host(), n4.port());

        assertEquals(beforeTotal, total(), "total must be preserved on removal");
        assertTrue(change.migrated() > 0, "n4 held records that must be relocated");
        // removed node no longer exists and holds nothing
        assertFalse(service.listNodes().contains(n4));
        assertEquals(0, totalOf(n4));
        // every surviving student is on its owner
        for (Student s : allStudentsNow()) {
            StorageNode owner = ring.getNode(s.getRollNo().toString());
            assertNotNull(store.findByRollNoOn(s.getRollNo(), owner));
        }
    }

    private List<Student> allStudentsNow() {
        List<Student> out = new ArrayList<>();
        for (StorageNode n : ring.getPhysicalNodes()) out.addAll(store.allStudentsOn(n));
        return out;
    }

    private int totalOf(StorageNode n) {
        return store.allStudentsOn(n).size();
    }

    private Map<String, Long> counts() {
        Map<String, Long> m = new java.util.HashMap<>();
        for (StorageNode n : ring.getPhysicalNodes()) m.put(n.key(), (long) store.allStudentsOn(n).size());
        return m;
    }
}
