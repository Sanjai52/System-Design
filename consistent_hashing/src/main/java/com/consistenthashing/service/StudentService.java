package com.consistenthashing.service;

import com.consistenthashing.consistenthash.ConsistentHashRing;
import com.consistenthashing.consistenthash.StorageNode;
import com.consistenthashing.model.Student;
import com.consistenthashing.storage.StudentStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentService.class);

    private final ConsistentHashRing ring;
    private final StudentStore store;

    public Student createStudent(Long rollNo, String name, String dept, int year) {
        Student student = Student.builder()
                .rollNo(rollNo)
                .name(name)
                .dept(dept)
                .year(year)
                .build();
        StorageNode owner = ring.getNode(rollNo.toString());
        store.insertOn(student, owner);
        log.info("Created student rollNo={} -> routed to {}", rollNo, owner.key());
        return student;
    }

    public Student getByRollNo(Long rollNo) {
        StorageNode owner = ring.getNode(rollNo.toString());
        return store.findByRollNoOn(rollNo, owner);
    }

    public List<Student> allStudents() {
        List<Student> all = new ArrayList<>();
        for (StorageNode node : ring.getPhysicalNodes()) {
            all.addAll(store.allStudentsOn(node));
        }
        return all;
    }

    public void deleteStudent(Long rollNo) {
        StorageNode owner = ring.getNode(rollNo.toString());
        store.deleteOn(rollNo, owner);
        log.info("Deleted student rollNo={} from {}", rollNo, owner.key());
    }
}
