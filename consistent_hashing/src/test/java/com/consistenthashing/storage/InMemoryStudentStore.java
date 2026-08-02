package com.consistenthashing.storage;

import com.consistenthashing.consistenthash.StorageNode;
import com.consistenthashing.model.Student;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryStudentStore implements StudentStore {

    private final Map<String, Map<Long, Student>> byNode = new ConcurrentHashMap<>();

    private Map<Long, Student> bucket(StorageNode node) {
        return byNode.computeIfAbsent(node.key(), k -> new ConcurrentHashMap<>());
    }

    @Override
    public List<Student> allStudentsOn(StorageNode node) {
        return new ArrayList<>(bucket(node).values());
    }

    @Override
    public Student findByRollNoOn(Long rollNo, StorageNode node) {
        return bucket(node).get(rollNo);
    }

    @Override
    public void insertOn(Student student, StorageNode node) {
        bucket(node).put(student.getRollNo(), student);
    }

    @Override
    public void deleteOn(Long rollNo, StorageNode node) {
        bucket(node).remove(rollNo);
    }

    @Override
    public long countOn(StorageNode node) {
        return bucket(node).size();
    }

    @Override
    public void dropOn(StorageNode node) {
        byNode.remove(node.key());
    }
}
