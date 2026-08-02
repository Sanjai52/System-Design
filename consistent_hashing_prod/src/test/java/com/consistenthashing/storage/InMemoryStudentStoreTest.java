package com.consistenthashing.storage;

import com.consistenthashing.consistenthash.StorageNode;
import com.consistenthashing.model.Student;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryStudentStoreTest {

    private final InMemoryStudentStore store = new InMemoryStudentStore();
    private final StorageNode n1 = new StorageNode("mongo1", 27017);
    private final StorageNode n2 = new StorageNode("mongo2", 27018);

    private Student student(Long rollNo) {
        return new Student(rollNo, "Name" + rollNo, "CS", 3);
    }

    @Test
    void testInsertFindDelete() {
        Student s = student(1001L);
        store.insertOn(s, n1);

        assertEquals(1, store.countOn(n1));
        Student found = store.findByRollNoOn(1001L, n1);
        assertNotNull(found);
        assertEquals("Name1001", found.getName());

        store.deleteOn(1001L, n1);
        assertNull(store.findByRollNoOn(1001L, n1));
        assertEquals(0, store.countOn(n1));
    }

    @Test
    void testIsolationBetweenNodes() {
        store.insertOn(student(1001L), n1);
        store.insertOn(student(1002L), n2);

        assertEquals(1, store.countOn(n1));
        assertEquals(1, store.countOn(n2));
        assertEquals(2, store.allStudentsOn(n1).size() + store.allStudentsOn(n2).size());
    }

    @Test
    void testDropClearsNode() {
        store.insertOn(student(1001L), n1);
        store.insertOn(student(1002L), n1);
        store.dropOn(n1);
        assertEquals(0, store.countOn(n1));
    }
}
