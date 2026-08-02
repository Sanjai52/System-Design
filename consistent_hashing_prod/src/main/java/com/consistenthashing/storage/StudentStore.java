package com.consistenthashing.storage;

import com.consistenthashing.consistenthash.StorageNode;
import com.consistenthashing.model.Student;

import java.util.List;

public interface StudentStore {

    List<Student> allStudentsOn(StorageNode node);

    Student findByRollNoOn(Long rollNo, StorageNode node);

    void insertOn(Student student, StorageNode node);

    void deleteOn(Long rollNo, StorageNode node);

    long countOn(StorageNode node);

    void dropOn(StorageNode node);
}
