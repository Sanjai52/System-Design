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

import static org.junit.jupiter.api.Assertions.*;

class StudentServiceTest {

    private ConsistentHashRing ring;
    private StudentStore store;
    private StudentService service;

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
        service = new StudentService(ring, store);
    }

    @Test
    void testCreateRoutesToRingOwner() {
        Student s = service.createStudent(1001L, "Alice", "CS", 3);

        StorageNode expectedOwner = ring.getNode(s.getRollNo().toString());
        assertNotNull(expectedOwner);
        assertEquals(1, store.countOn(expectedOwner));
        assertEquals("Alice", store.findByRollNoOn(1001L, expectedOwner).getName());
    }

    @Test
    void testGetByRollNoReturnsStudent() {
        service.createStudent(1002L, "Bob", "EC", 2);

        Student found = service.getByRollNo(1002L);
        assertNotNull(found);
        assertEquals("Bob", found.getName());
        assertEquals("EC", found.getDept());
        assertEquals(2, found.getYear());
    }

    @Test
    void testAllStudentsAggregatesAcrossNodes() {
        service.createStudent(1001L, "Alice", "CS", 3);
        service.createStudent(1002L, "Bob", "EC", 2);
        service.createStudent(1003L, "Charlie", "ME", 4);

        List<Student> all = service.allStudents();
        assertEquals(3, all.size());
    }

    @Test
    void testDeleteRemovesFromOwner() {
        service.createStudent(1001L, "Alice", "CS", 3);
        assertNotNull(service.getByRollNo(1001L));

        service.deleteStudent(1001L);
        assertNull(service.getByRollNo(1001L));
    }

    @Test
    void testGetByRollNoMissingReturnsNull() {
        assertNull(service.getByRollNo(9999L));
    }
}
